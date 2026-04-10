import {useCallback, useEffect, useRef, useState} from 'react';
import {api} from '../services/api';
import './ContactPage.css';

function ContactPage() {
    const [formData, setFormData] = useState({
        name: '',
        email: '',
        subject: '',
        message: ''
    });
    const [errors, setErrors] = useState({});
    const [submitted, setSubmitted] = useState(false);
    const [sending, setSending] = useState(false);
    const [sendError, setSendError] = useState('');
    const [turnstileToken, setTurnstileToken] = useState('');
    const turnstileRef = useRef(null);
    const widgetIdRef = useRef(null);

    const renderTurnstile = useCallback(() => {
        if (window.turnstile && turnstileRef.current && widgetIdRef.current === null) {
            widgetIdRef.current = window.turnstile.render(turnstileRef.current, {
                sitekey: process.env.REACT_APP_TURNSTILE_SITE_KEY,
                callback: (token) => setTurnstileToken(token),
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
        const newErrors = {};
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

    const handleInputChange = (e) => {
        const {name, value} = e.target;
        setFormData(prev => ({...prev, [name]: value}));
        if (errors[name]) {
            setErrors(prev => ({...prev, [name]: ''}));
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (validateForm()) {
            setSending(true);
            setSendError('');
            try {
                await api.submitContactForm({...formData, turnstileToken});
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

    return (
        <div className="contact-page">
            <section className="page-hero">
                <h1>Contact Us</h1>
                <p>Have a question or want to plan a group trip? We'd love to hear from you.</p>
            </section>

            <div className="contact-layout">
                <div className="contact-info">
                    <div className="contact-info-card">
                        <h3>Get in Touch</h3>
                        <p>Whether you have a question about destinations, pricing, or anything else, our team is ready
                            to help.</p>

                        <div className="contact-details">
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">Email</span>
                                <a href="mailto:info@trivlu.com">info@trivlu.com</a>
                            </div>
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">Response Time</span>
                                <span>Within 24 hours</span>
                            </div>
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">Company</span>
                                <span>Pragout group s.r.o.</span>
                            </div>
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">Address</span>
                                <span>Na Folimance 2155/15, Vinohrady, 120 00 Prague 2, Czech Republic</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="contact-form-section">
                    {submitted ? (
                        <div className="contact-success">
                            <h3>Message Sent!</h3>
                            <p>Thanks, {formData.name}. We'll get back to you
                                at <strong>{formData.email}</strong> within 24 hours.</p>
                            <button className="btn btn--primary" onClick={() => {
                                setSubmitted(false);
                                setFormData({name: '', email: '', subject: '', message: ''});
                                setTurnstileToken('');
                                if (window.turnstile && widgetIdRef.current !== null) {
                                    window.turnstile.reset(widgetIdRef.current);
                                }
                            }}>
                                Send Another Message
                            </button>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="contact-form">
                            <div className="form-row">
                                <div className="form-group">
                                    <label htmlFor="name">Name *</label>
                                    <input
                                        type="text" id="name" name="name"
                                        value={formData.name} onChange={handleInputChange}
                                        className={errors.name ? 'error' : ''}
                                        placeholder="Your name"
                                    />
                                    {errors.name && <span className="error-message">{errors.name}</span>}
                                </div>
                                <div className="form-group">
                                    <label htmlFor="email">Email *</label>
                                    <input
                                        type="email" id="email" name="email"
                                        value={formData.email} onChange={handleInputChange}
                                        className={errors.email ? 'error' : ''}
                                        placeholder="you@example.com"
                                    />
                                    {errors.email && <span className="error-message">{errors.email}</span>}
                                </div>
                            </div>

                            <div className="form-group">
                                <label htmlFor="subject">Subject *</label>
                                <select
                                    id="subject" name="subject"
                                    value={formData.subject} onChange={handleInputChange}
                                    className={errors.subject ? 'error' : ''}
                                >
                                    <option value="">Select a topic</option>
                                    <option value="Group Trip Inquiry">Group Trip Inquiry</option>
                                    <option value="Pricing & Packages">Pricing & Packages</option>
                                    <option value="Partnership">Partnership</option>
                                    <option value="Support">Support</option>
                                    <option value="Other">Other</option>
                                </select>
                                {errors.subject && <span className="error-message">{errors.subject}</span>}
                            </div>

                            <div className="form-group">
                                <label htmlFor="message">Message *</label>
                                <textarea
                                    id="message" name="message"
                                    value={formData.message} onChange={handleInputChange}
                                    className={errors.message ? 'error' : ''}
                                    rows="5"
                                    placeholder="Tell us how we can help..."
                                />
                                {errors.message && <span className="error-message">{errors.message}</span>}
                            </div>

                            <div className="form-group">
                                <div ref={turnstileRef}></div>
                                {errors.turnstile && <span className="error-message">{errors.turnstile}</span>}
                            </div>

                            {sendError && <div className="contact-error">{sendError}</div>}
                            <button type="submit" className="btn btn--primary btn--full-width" disabled={sending}>
                                {sending ? 'Sending...' : 'Send Message'}
                            </button>
                        </form>
                    )}
                </div>
            </div>
        </div>
    );
}

export default ContactPage;
