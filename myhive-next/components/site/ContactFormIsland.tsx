'use client';

// Interactive contact form extracted from legacy-src/pages/ContactPage.js.
// Same fields, validation, Turnstile flow and payload; POSTs same-origin to
// /api/contact (the rewrite proxies to the Spring backend, matching the legacy
// api.submitContactForm call whose API_BASE_URL is bridged to '/api'). The
// Turnstile loader script lives in the root layout; we render explicitly via
// window.turnstile once it's available.
import { useCallback, useEffect, useRef, useState } from 'react';

declare global {
  interface Window {
    turnstile?: {
      render: (el: HTMLElement, opts: Record<string, unknown>) => string;
      reset: (id?: string) => void;
    };
  }
}

interface FormData {
  name: string;
  email: string;
  subject: string;
  message: string;
}

const EMPTY: FormData = { name: '', email: '', subject: '', message: '' };

export default function ContactFormIsland() {
  const [formData, setFormData] = useState<FormData>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitted, setSubmitted] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [turnstileToken, setTurnstileToken] = useState('');
  const turnstileRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<string | null>(null);

  const renderTurnstile = useCallback(() => {
    if (window.turnstile && turnstileRef.current && widgetIdRef.current === null) {
      widgetIdRef.current = window.turnstile.render(turnstileRef.current, {
        sitekey: process.env.REACT_APP_TURNSTILE_SITE_KEY,
        callback: (token: string) => setTurnstileToken(token),
        'expired-callback': () => setTurnstileToken(''),
      });
    }
  }, []);

  useEffect(() => {
    if (window.turnstile) {
      renderTurnstile();
    } else {
      const interval = setInterval(() => {
        if (window.turnstile) {
          clearInterval(interval);
          renderTurnstile();
        }
      }, 100);
      return () => clearInterval(interval);
    }
  }, [renderTurnstile]);

  const validateForm = () => {
    const newErrors: Record<string, string> = {};
    if (!formData.name.trim()) newErrors.name = 'Name is required';
    if (!formData.email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Email is invalid';
    }
    if (!formData.subject.trim()) newErrors.subject = 'Subject is required';
    if (!formData.message.trim()) newErrors.message = 'Message is required';
    if (!turnstileToken) newErrors.turnstile = 'Please complete the verification';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleInputChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: '' }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (validateForm()) {
      setSending(true);
      setSendError('');
      try {
        const res = await fetch('/api/contact', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...formData, turnstileToken }),
        });
        if (!res.ok) throw new Error('Failed to send message');
        setSubmitted(true);
        if (window.turnstile && widgetIdRef.current !== null) {
          window.turnstile.reset(widgetIdRef.current);
        }
        setTurnstileToken('');
      } catch {
        setSendError('Failed to send message. Please try again or email us directly.');
      } finally {
        setSending(false);
      }
    }
  };

  if (submitted) {
    return (
      <div className="contact-success">
        <h3>Message Sent!</h3>
        <p>
          Thanks, {formData.name}. We&apos;ll get back to you at{' '}
          <strong>{formData.email}</strong> within 24 hours.
        </p>
        <button
          className="btn btn--primary"
          onClick={() => {
            setSubmitted(false);
            setFormData(EMPTY);
            setTurnstileToken('');
            if (window.turnstile && widgetIdRef.current !== null) {
              window.turnstile.reset(widgetIdRef.current);
            }
          }}
        >
          Send Another Message
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="contact-form">
      <div className="form-row">
        <div className="form-group">
          <label htmlFor="name">Name *</label>
          <input
            type="text"
            id="name"
            name="name"
            value={formData.name}
            onChange={handleInputChange}
            className={errors.name ? 'error' : ''}
            placeholder="Your name"
          />
          {errors.name && <span className="error-message">{errors.name}</span>}
        </div>
        <div className="form-group">
          <label htmlFor="email">Email *</label>
          <input
            type="email"
            id="email"
            name="email"
            value={formData.email}
            onChange={handleInputChange}
            className={errors.email ? 'error' : ''}
            placeholder="you@example.com"
          />
          {errors.email && <span className="error-message">{errors.email}</span>}
        </div>
      </div>

      <div className="form-group">
        <label htmlFor="subject">Subject *</label>
        <select
          id="subject"
          name="subject"
          value={formData.subject}
          onChange={handleInputChange}
          className={errors.subject ? 'error' : ''}
        >
          <option value="">Select a topic</option>
          <option value="Group Trip Inquiry">Group Trip Inquiry</option>
          <option value="Pricing & Packages">Pricing &amp; Packages</option>
          <option value="Partnership">Partnership</option>
          <option value="Support">Support</option>
          <option value="Other">Other</option>
        </select>
        {errors.subject && <span className="error-message">{errors.subject}</span>}
      </div>

      <div className="form-group">
        <label htmlFor="message">Message *</label>
        <textarea
          id="message"
          name="message"
          value={formData.message}
          onChange={handleInputChange}
          className={errors.message ? 'error' : ''}
          rows={5}
          placeholder="Tell us how we can help..."
        />
        {errors.message && <span className="error-message">{errors.message}</span>}
      </div>

      <div className="form-group">
        <div ref={turnstileRef}></div>
        {errors.turnstile && <span className="error-message">{errors.turnstile}</span>}
      </div>

      {sendError && <div className="contact-error">{sendError}</div>}
      <button
        type="submit"
        className="btn btn--primary btn--full-width"
        disabled={sending}
      >
        {sending ? 'Sending...' : 'Send Message'}
      </button>
    </form>
  );
}
