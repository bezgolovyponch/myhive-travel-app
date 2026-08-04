import {useEffect, useId, useState} from 'react';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';
import AppModal from './AppModal';
import EmailConsentNote from './EmailConsentNote';
import {computeTripTotal} from '../utils/tripPricing';
import {formatPrice} from '../utils/format';

function ContactForm({isOpen, onClose, onSubmit, submitLabel = 'Submit Booking', inline = false,
                      tripData, initialValues, isSubmitting, submitError,
                      onEmailChange, showConsentNote = false}) {
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
            newErrors.fullName = 'Full name is required';
        }

        if (!formData.email.trim()) {
            newErrors.email = 'Email is required';
        } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
            newErrors.email = 'Email is invalid';
        }

        if (!formData.phone.trim()) {
            newErrors.phone = 'Phone number is required';
        } else if (!/^\+?[\d\s\-()]+$/.test(formData.phone)) {
            newErrors.phone = 'Phone number is invalid';
        }

        if (!formData.startDate) {
            newErrors.startDate = 'Start date is required';
        }

        if (!formData.endDate) {
            newErrors.endDate = 'End date is required';
        } else if (formData.startDate && formData.endDate && new Date(formData.endDate) <= new Date(formData.startDate)) {
            newErrors.endDate = 'End date must be after start date (minimum 1 night)';
        }

        if (formData.numberOfTravelers < 1) {
            newErrors.numberOfTravelers = 'Number of travelers must be at least 1';
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

    const body = (
        <>
                    {/* Inline mode skips the summary — the itinerary + total are already
                        visible in the column right next to the form. */}
                    {!inline && (
                        <div className="trip-summary">
                            <h4>Trip Summary</h4>
                            {tripData.destinationName && (
                                <p><strong>Destination:</strong> {tripData.destinationName}</p>
                            )}
                            <p><strong>Activities:</strong> {tripData.tripItems.length} selected</p>
                            <p><strong>Estimated Total:</strong> {formatPrice(computeTripTotal(tripData.tripItems, Number(formData.numberOfTravelers) || 1))}</p>
                        </div>
                    )}

                    {submitError && (
                        <div className="form-error">{submitError}</div>
                    )}

                    <form id={formId} onSubmit={handleSubmit} className="contact-form">
                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="fullName">Full Name *</label>
                                <input
                                    type="text" id="fullName" name="fullName"
                                    value={formData.fullName} onChange={handleInputChange}
                                    className={errors.fullName ? 'error' : ''}
                                    placeholder="John Doe"
                                />
                                {errors.fullName && <span className="error-message">{errors.fullName}</span>}
                            </div>
                            <div className="form-group">
                                <label htmlFor="email">Email Address *</label>
                                <input
                                    type="email" id="email" name="email"
                                    value={formData.email} onChange={handleInputChange}
                                    className={errors.email ? 'error' : ''}
                                    placeholder="john@example.com"
                                />
                                {errors.email && <span className="error-message">{errors.email}</span>}
                                {showConsentNote && (
                                    <>
                                        <p className="email-value-note">
                                            We&apos;ll save your trip to this address so you can pick it up anytime.
                                        </p>
                                        <EmailConsentNote />
                                    </>
                                )}
                            </div>
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="phone">Phone Number *</label>
                                <input
                                    type="tel" id="phone" name="phone"
                                    value={formData.phone} onChange={handleInputChange}
                                    className={errors.phone ? 'error' : ''}
                                    placeholder="+1 234 567 8900"
                                />
                                {errors.phone && <span className="error-message">{errors.phone}</span>}
                            </div>
                            <div className="form-group">
                                <label htmlFor="numberOfTravelers">Number of Travelers *</label>
                                {/* Same compact − / n / + stepper as the trip setup modal */}
                                <div className="travelers-control">
                                    <button
                                        type="button"
                                        className="travelers-step"
                                        aria-label="Decrease travelers"
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
                                        aria-label="Increase travelers"
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
                                <label htmlFor="contactMethod">Preferred Contact Method *</label>
                                <select id="contactMethod" name="contactMethod"
                                        value={formData.contactMethod} onChange={handleInputChange}>
                                    <option value="email">Email</option>
                                    <option value="phone">Phone</option>
                                    <option value="whatsapp">WhatsApp</option>
                                    <option value="both">Both</option>
                                </select>
                            </div>
                            <div className="form-group">
                                <label htmlFor="hearAboutUs">How did you hear about us?</label>
                                <select id="hearAboutUs" name="hearAboutUs"
                                        value={formData.hearAboutUs} onChange={handleInputChange}>
                                    <option value="">Select an option</option>
                                    <option value="search">Search Engine</option>
                                    <option value="social">Social Media</option>
                                    <option value="friend">Friend/Family</option>
                                    <option value="ad">Advertisement</option>
                                    <option value="blog">Blog/Article</option>
                                    <option value="other">Other</option>
                                </select>
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="specialRequirements">Special Requirements or Notes</label>
                            <textarea
                                id="specialRequirements" name="specialRequirements"
                                value={formData.specialRequirements} onChange={handleInputChange}
                                rows={inline ? 2 : 3}
                                placeholder="Any special dietary requirements, accessibility needs, or other preferences..."
                            />
                        </div>
                    </form>
        </>
    );

    const footer = (
        <>
            <button type="button" className="btn btn--secondary" onClick={guardedClose} disabled={isSubmitting}>
                Cancel
            </button>
            <button type="submit" form={formId} className="btn btn--primary" disabled={isSubmitting}>
                {isSubmitting ? 'Submitting...' : submitLabel}
            </button>
        </>
    );

    if (inline) {
        // Trip Builder renders the form in place of its browse column — no overlay.
        return (
            <div className="contact-form-panel">
                <h3 className="contact-form-panel__title">Complete Your Booking</h3>
                <p className="contact-form-panel__step">
                    Step 1 of 2 — we confirm availability, then you pay a 30% deposit.
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
            title="Complete Your Booking"
            contentClassName="contact-form-modal"
            footer={footer}
        >
            {body}
        </AppModal>
    );
}

export default ContactForm;
