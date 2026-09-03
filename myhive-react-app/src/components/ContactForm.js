import {useEffect, useId, useState} from 'react';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';
import AppModal from './AppModal';
import EmailConsentNote from './EmailConsentNote';
import {computeTripTotal} from '../utils/tripPricing';
import {formatPrice} from '../utils/format';
import {useT} from '../i18n';

function ContactForm({isOpen, onClose, onSubmit, submitLabel, inline = false,
                      tripData, initialValues, isSubmitting, submitError,
                      onEmailChange, showConsentNote = false}) {
    const t = useT('contact');
    const [formData, setFormData] = useState({
        fullName: '',
        email: '',
        phone: '',
        numberOfTravelers: initialValues?.numberOfTravelers || 1,
        startDate: initialValues?.startDate || '',
        endDate: initialValues?.endDate || '',
        contactMethod: 'email',
        specialRequirements: '',
        hearAboutUs: ''
    });

    useEffect(() => {
        if (isOpen && initialValues) {
            setFormData(prev => ({
                ...prev,
                numberOfTravelers: initialValues.numberOfTravelers || prev.numberOfTravelers,
                startDate: initialValues.startDate || prev.startDate,
                endDate: initialValues.endDate || prev.endDate
            }));
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen]);

    const [errors, setErrors] = useState({});
    // The submit button lives outside the <form> (modal footer / panel footer);
    // the form attribute associates them so a click runs native constraint
    // validation (e.g. travelers min/max) before our handleSubmit fires.
    const formId = useId();
    // Escape and × must respect the same guard as the disabled Cancel button:
    // closing mid-submit invites a duplicate booking.
    const guardedClose = () => {
        if (!isSubmitting) {
            onClose();
        }
    };

    const validateForm = () => {
        const newErrors = {};

        if (!formData.fullName.trim()) {
            newErrors.fullName = t('booking.validation.fullNameRequired');
        }

        if (!formData.email.trim()) {
            newErrors.email = t('booking.validation.emailRequired');
        } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
            newErrors.email = t('booking.validation.emailInvalid');
        }

        if (!formData.phone.trim()) {
            newErrors.phone = t('booking.validation.phoneRequired');
        } else if (!/^\+?[\d\s\-()]+$/.test(formData.phone)) {
            newErrors.phone = t('booking.validation.phoneInvalid');
        }

        if (!formData.startDate) {
            newErrors.startDate = t('booking.validation.startDateRequired');
        }

        if (!formData.endDate) {
            newErrors.endDate = t('booking.validation.endDateRequired');
        } else if (formData.startDate && formData.endDate && new Date(formData.endDate) <= new Date(formData.startDate)) {
            newErrors.endDate = t('booking.validation.endDateAfterStart');
        }

        if (formData.numberOfTravelers < 1) {
            newErrors.numberOfTravelers = t('booking.validation.travelersMin');
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleInputChange = (e) => {
        const {name, value} = e.target;
        setFormData(prev => ({...prev, [name]: value}));
        if (name === 'email' && onEmailChange) {
            onEmailChange(value);
        }
        if (errors[name]) {
            setErrors(prev => ({...prev, [name]: ''}));
        }
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (validateForm()) {
            onSubmit(formData);
        }
    };

    if (!isOpen) return null;

    // Always priced off this form's own traveler count — the stepper below can
    // change it, and the figure the user reads has to be the one that gets
    // submitted.
    const tripTotal = formatPrice(computeTripTotal(tripData.tripItems, Number(formData.numberOfTravelers) || 1));

    const body = (
        <>
                    {/* Inline mode (Trip Builder) shows the total alone — the itinerary is
                        already on screen next to the form; the modal wraps the same figure
                        in a summary with destination and activity count. */}
                    {inline ? (
                        <div className="booking-total">
                            <span>{t('booking.tripTotalLabel')}</span>
                            <span className="booking-total-price">{tripTotal}</span>
                        </div>
                    ) : (
                        <div className="trip-summary">
                            <h4>{t('booking.summaryTitle')}</h4>
                            {tripData.destinationName && (
                                <p><strong>{t('booking.destinationLabel')}</strong> {tripData.destinationName}</p>
                            )}
                            <p><strong>{t('booking.activitiesLabel')}</strong> {t('booking.activitiesSelected', {count: tripData.tripItems.length})}</p>
                            <p><strong>{t('booking.estimatedTotalLabel')}</strong> {tripTotal}</p>
                        </div>
                    )}

                    {submitError && (
                        <div className="form-error">{submitError}</div>
                    )}

                    <form id={formId} onSubmit={handleSubmit} className="contact-form">
                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="fullName">{t('booking.fullNameLabel')}</label>
                                <input
                                    type="text" id="fullName" name="fullName"
                                    value={formData.fullName} onChange={handleInputChange}
                                    className={errors.fullName ? 'error' : ''}
                                    placeholder={t('booking.fullNamePlaceholder')}
                                />
                                {errors.fullName && <span className="error-message">{errors.fullName}</span>}
                            </div>
                            <div className="form-group">
                                <label htmlFor="email">{t('booking.emailLabel')}</label>
                                <input
                                    type="email" id="email" name="email"
                                    value={formData.email} onChange={handleInputChange}
                                    className={errors.email ? 'error' : ''}
                                    placeholder={t('booking.emailPlaceholder')}
                                />
                                {errors.email && <span className="error-message">{errors.email}</span>}
                                {showConsentNote && (
                                    <>
                                        <p className="email-value-note">
                                            {t('booking.emailSaveNote')}
                                        </p>
                                        <EmailConsentNote />
                                    </>
                                )}
                            </div>
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="phone">{t('booking.phoneLabel')}</label>
                                <input
                                    type="tel" id="phone" name="phone"
                                    value={formData.phone} onChange={handleInputChange}
                                    className={errors.phone ? 'error' : ''}
                                    placeholder={t('booking.phonePlaceholder')}
                                />
                                {errors.phone && <span className="error-message">{errors.phone}</span>}
                            </div>
                            <div className="form-group">
                                <label htmlFor="numberOfTravelers">{t('booking.travelersLabel')}</label>
                                {/* Same compact − / n / + stepper as the trip setup modal */}
                                <div className="travelers-control">
                                    <button
                                        type="button"
                                        className="travelers-step"
                                        aria-label={t('booking.decreaseTravelers')}
                                        disabled={(parseInt(formData.numberOfTravelers, 10) || 1) <= 1}
                                        onClick={() => setFormData(prev => ({
                                            ...prev,
                                            numberOfTravelers: Math.max(1, (parseInt(prev.numberOfTravelers, 10) || 1) - 1),
                                        }))}
                                    >−</button>
                                    <input
                                        type="number" id="numberOfTravelers" name="numberOfTravelers"
                                        value={formData.numberOfTravelers} onChange={handleInputChange}
                                        min="1" max="20"
                                        className={`travelers-count${errors.numberOfTravelers ? ' error' : ''}`}
                                    />
                                    <button
                                        type="button"
                                        className="travelers-step"
                                        aria-label={t('booking.increaseTravelers')}
                                        onClick={() => setFormData(prev => ({
                                            ...prev,
                                            numberOfTravelers: Math.min(20, (parseInt(prev.numberOfTravelers, 10) || 1) + 1),
                                        }))}
                                    >+</button>
                                </div>
                                {errors.numberOfTravelers &&
                                    <span className="error-message">{errors.numberOfTravelers}</span>}
                            </div>
                        </div>

                        <DateRangePicker
                            from={formData.startDate}
                            to={formData.endDate}
                            collapsible={inline}
                            onChange={(from, to) => {
                                setFormData(prev => ({ ...prev, startDate: from, endDate: to }));
                                setErrors(prev => ({ ...prev, startDate: '', endDate: '' }));
                            }}
                        />
                        {(errors.startDate || errors.endDate) && (
                            <span className="error-message">{errors.startDate || errors.endDate}</span>
                        )}

                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="contactMethod">{t('booking.contactMethodLabel')}</label>
                                <select id="contactMethod" name="contactMethod"
                                        value={formData.contactMethod} onChange={handleInputChange}>
                                    <option value="email">{t('booking.contactMethodOptions.email')}</option>
                                    <option value="phone">{t('booking.contactMethodOptions.phone')}</option>
                                    <option value="whatsapp">{t('booking.contactMethodOptions.whatsapp')}</option>
                                    <option value="both">{t('booking.contactMethodOptions.both')}</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="hearAboutUs">{t('booking.hearAboutUsLabel')}</label>
                                <select id="hearAboutUs" name="hearAboutUs"
                                        value={formData.hearAboutUs} onChange={handleInputChange}>
                                    <option value="">{t('booking.hearAboutUsOptions.placeholder')}</option>
                                    <option value="search">{t('booking.hearAboutUsOptions.search')}</option>
                                    <option value="social">{t('booking.hearAboutUsOptions.social')}</option>
                                    <option value="friend">{t('booking.hearAboutUsOptions.friend')}</option>
                                    <option value="ad">{t('booking.hearAboutUsOptions.ad')}</option>
                                    <option value="blog">{t('booking.hearAboutUsOptions.blog')}</option>
                                    <option value="other">{t('booking.hearAboutUsOptions.other')}</option>
                                </select>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="specialRequirements">{t('booking.specialRequirementsLabel')}</label>
                            <textarea
                                id="specialRequirements" name="specialRequirements"
                                value={formData.specialRequirements} onChange={handleInputChange}
                                rows={inline ? 2 : 3}
                                placeholder={t('booking.specialRequirementsPlaceholder')}
                            />
                        </div>
                    </form>
        </>
    );

    const footer = (
        <>
            <button type="button" className="btn btn--secondary" onClick={guardedClose} disabled={isSubmitting}>
                {t('booking.cancel')}
            </button>
            <button type="submit" form={formId} className="btn btn--primary" disabled={isSubmitting}>
                {isSubmitting ? t('booking.submitting') : (submitLabel ?? t('booking.submitLabel'))}
            </button>
        </>
    );

    if (inline) {
        // Trip Builder renders the form in place of its browse column — no overlay.
        return (
            <div className="contact-form-panel">
                <h3 className="contact-form-panel__title">{t('booking.title')}</h3>
                <p className="contact-form-panel__step">
                    {t('booking.step')}
                </p>
                <div className="contact-form-panel__body">{body}</div>
                <div className="contact-form-panel__footer">{footer}</div>
            </div>
        );
    }

    return (
        <AppModal
            isOpen
            onClose={guardedClose}
            title={t('booking.title')}
            contentClassName="contact-form-modal"
            footer={footer}
        >
            {body}
        </AppModal>
    );
}

export default ContactForm;
