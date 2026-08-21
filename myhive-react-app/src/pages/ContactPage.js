import PageHead from '../components/PageHead';
import {useCallback, useEffect, useRef, useState} from 'react';
import {api} from '../services/api';
import {SITE_URL} from '../services/config';
import {useT} from '../i18n';
import './ContactPage.css';

function ContactPage() {
    const t = useT('contact');
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
        if (!formData.name.trim()) newErrors.name = t('validation.nameRequired');
        if (!formData.email.trim()) {
            newErrors.email = t('validation.emailRequired');
        } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
            newErrors.email = t('validation.emailInvalid');
        }
        if (!formData.subject.trim()) newErrors.subject = t('validation.subjectRequired');
        if (!formData.message.trim()) newErrors.message = t('validation.messageRequired');
        if (!turnstileToken) newErrors.turnstile = t('validation.turnstileRequired');
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
                setSendError(t('sendError'));
            } finally {
                setSending(false);
            }
        }
    };

    return (
        <div className="contact-page">
            <PageHead>
                <title>Contact Trivlu — Talk to the Team</title>
                <meta name="description"
                      content="Questions about your stag do or booking? Reach the Trivlu team by WhatsApp, Messenger or email — we're quick to reply."/>
                <link rel="canonical" href={`${SITE_URL}/contact`}/>
            </PageHead>
            <section className="page-hero">
                <h1>{t('hero.title')}</h1>
                <p>{t('hero.subtitle')}</p>
            </section>

            <div className="contact-layout">
                <div className="contact-info">
                    <div className="contact-info-card">
                        <h3>{t('info.title')}</h3>
                        <p>{t('info.body')}</p>

                        <div className="contact-details">
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">{t('info.emailLabel')}</span>
                                <a href="mailto:info@trivlu.com">info@trivlu.com</a>
                            </div>
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">{t('info.responseTimeLabel')}</span>
                                <span>{t('info.responseTimeValue')}</span>
                            </div>
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">{t('info.companyLabel')}</span>
                                <span>Pragout group s.r.o.</span>
                            </div>
                            <div className="contact-detail-item">
                                <span className="contact-detail-label">{t('info.addressLabel')}</span>
                                <span>{t('info.addressValue')}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="contact-form-section">
                    {submitted ? (
                        <div className="contact-success">
                            <h3>{t('success.title')}</h3>
                            <p>{t('success.textBeforeEmail', {name: formData.name})}{' '}
                                <strong>{formData.email}</strong> {t('success.textAfterEmail')}</p>
                            <button className="btn btn--primary" onClick={() => {
                                setSubmitted(false);
                                setFormData({name: '', email: '', subject: '', message: ''});
                                setTurnstileToken('');
                                if (window.turnstile && widgetIdRef.current !== null) {
                                    window.turnstile.reset(widgetIdRef.current);
                                }
                            }}>
                                {t('success.sendAnother')}
                            </button>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="contact-form">
                            <div className="form-row">
                                <div className="form-group">
                                    <label htmlFor="name">{t('form.nameLabel')}</label>
                                    <input
                                        type="text" id="name" name="name"
                                        value={formData.name} onChange={handleInputChange}
                                        className={errors.name ? 'error' : ''}
                                        placeholder={t('form.namePlaceholder')}
                                    />
                                    {errors.name && <span className="error-message">{errors.name}</span>}
                                </div>
                                <div className="form-group">
                                    <label htmlFor="email">{t('form.emailLabel')}</label>
                                    <input
                                        type="email" id="email" name="email"
                                        value={formData.email} onChange={handleInputChange}
                                        className={errors.email ? 'error' : ''}
                                        placeholder={t('form.emailPlaceholder')}
                                    />
                                    {errors.email && <span className="error-message">{errors.email}</span>}
                                </div>
                            </div>

                            <div className="form-group">
                                <label htmlFor="subject">{t('form.subjectLabel')}</label>
                                <select
                                    id="subject" name="subject"
                                    value={formData.subject} onChange={handleInputChange}
                                    className={errors.subject ? 'error' : ''}
                                >
                                    <option value="">{t('form.subjectOptions.placeholder')}</option>
                                    <option value="Group Trip Inquiry">{t('form.subjectOptions.groupTrip')}</option>
                                    <option value="Pricing & Packages">{t('form.subjectOptions.pricing')}</option>
                                    <option value="Partnership">{t('form.subjectOptions.partnership')}</option>
                                    <option value="Support">{t('form.subjectOptions.support')}</option>
                                    <option value="Other">{t('form.subjectOptions.other')}</option>
                                </select>
                                {errors.subject && <span className="error-message">{errors.subject}</span>}
                            </div>

                            <div className="form-group">
                                <label htmlFor="message">{t('form.messageLabel')}</label>
                                <textarea
                                    id="message" name="message"
                                    value={formData.message} onChange={handleInputChange}
                                    className={errors.message ? 'error' : ''}
                                    rows="5"
                                    placeholder={t('form.messagePlaceholder')}
                                />
                                {errors.message && <span className="error-message">{errors.message}</span>}
                            </div>

                            <div className="form-group">
                                <div ref={turnstileRef}></div>
                                {errors.turnstile && <span className="error-message">{errors.turnstile}</span>}
                            </div>

                            {sendError && <div className="contact-error">{sendError}</div>}
                            <button type="submit" className="btn btn--primary btn--full-width" disabled={sending}>
                                {sending ? t('form.sending') : t('form.send')}
                            </button>
                        </form>
                    )}
                </div>
            </div>
        </div>
    );
}

export default ContactPage;
