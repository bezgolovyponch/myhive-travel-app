import {useEffect, useState} from 'react';
import './ContactForm.css';
import DateRangePicker from './DateRangePicker';
import {computeTripTotal} from '../utils/tripPricing';
import {useModalA11y} from '../hooks/useModalA11y';

function ContactForm({isOpen, onClose, onSubmit, tripData, initialValues, isSubmitting, submitError}) {
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
    // Escape must respect the same guard as the disabled Cancel button:
    // closing mid-submit invites a duplicate booking.
    const contentRef = useModalA11y(isOpen, () => {
        if (!isSubmitting) {
            onClose();
        }
    });

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
        } else if (!/^\+?[\d\s\-\(\)]+$/.test(formData.phone)) {
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

    return (
        <div className="app-modal" role="dialog" aria-modal="true" aria-labelledby="contact-form-title">
            <div className="app-modal-content contact-form-modal" ref={contentRef}>
                <div className="app-modal-header">
                    <h2 id="contact-form-title">Complete Your Booking</h2>
                    <button type="button" className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
                </div>

                <div className="app-modal-body">
                    <div className="trip-summary">
                        <h4>Trip Summary</h4>
                        <p><strong>Activities:</strong> {tripData.tripItems.length} selected</p>
                        <p><strong>Estimated Total:</strong> €{computeTripTotal(tripData.tripItems, Number(formData.numberOfTravelers) || 1).toFixed(2)}</p>
                    </div>

                    {submitError && (
                        <div className="form-error">{submitError}</div>
                    )}

                    <form onSubmit={handleSubmit} className="contact-form">
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
                                <input
                                    type="number" id="numberOfTravelers" name="numberOfTravelers"
                                    value={formData.numberOfTravelers} onChange={handleInputChange}
                                    min="1" max="20"
                                    className={errors.numberOfTravelers ? 'error' : ''}
                                />
                                {errors.numberOfTravelers &&
                                    <span className="error-message">{errors.numberOfTravelers}</span>}
                            </div>
                        </div>

                        <DateRangePicker
                            from={formData.startDate}
                            to={formData.endDate}
                            onChange={(from, to) => {
                                setFormData(prev => ({ ...prev, startDate: from, endDate: to }));
                                setErrors(prev => ({ ...prev, startDate: '', endDate: '' }));
                            }}
                        />
                        {(errors.startDate || errors.endDate) && (
                            <span className="error-message">{errors.startDate || errors.endDate}</span>
                        )}

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

                        <div className="form-group">
                            <label htmlFor="specialRequirements">Special Requirements or Notes</label>
                            <textarea
                                id="specialRequirements" name="specialRequirements"
                                value={formData.specialRequirements} onChange={handleInputChange}
                                rows="3"
                                placeholder="Any special dietary requirements, accessibility needs, or other preferences..."
                            />
                        </div>
                    </form>
                </div>

                <div className="app-modal-footer">
                    <button className="btn btn--secondary" onClick={onClose} disabled={isSubmitting}>
                        Cancel
                    </button>
                    <button className="btn btn--primary" onClick={handleSubmit} disabled={isSubmitting}>
                        {isSubmitting ? 'Submitting...' : 'Submit Booking'}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default ContactForm;
