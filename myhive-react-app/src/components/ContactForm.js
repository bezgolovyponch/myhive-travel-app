import React, {useState} from 'react';

function ContactForm({isOpen, onClose, onSubmit, tripData, isSubmitting, submitError}) {
    const [formData, setFormData] = useState({
        fullName: '',
        email: '',
        phone: '',
        numberOfTravelers: 1,
        startDate: '',
        endDate: '',
        contactMethod: 'email',
        specialRequirements: '',
        hearAboutUs: ''
    });

    const [errors, setErrors] = useState({});

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
        } else if (formData.startDate && formData.endDate && new Date(formData.endDate) < new Date(formData.startDate)) {
            newErrors.endDate = 'End date must be after start date';
        }

        if (formData.numberOfTravelers < 1) {
            newErrors.numberOfTravelers = 'Number of travelers must be at least 1';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleInputChange = (e) => {
        const {name, value} = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: value
        }));

        // Clear error for this field when user starts typing
        if (errors[name]) {
            setErrors(prev => ({
                ...prev,
                [name]: ''
            }));
        }
    };

    const handleSubmit = (e) => {
        e.preventDefault();

        if (validateForm()) {
            onSubmit(formData);
        }
    };

    const calculateTotalPrice = () => {
        return tripData.tripItems.reduce((total, item) => {
            const price = typeof item.price === 'number' ? item.price :
                parseFloat(item.price?.replace(/[^0-9.]/g, '')) || 0;
            return total + (price * formData.numberOfTravelers);
        }, 0);
    };

    if (!isOpen) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content contact-form-modal">
                <div className="modal-header">
                    <h3>Complete Your Booking</h3>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <div className="modal-body">
                    <div className="trip-summary">
                        <h4>Trip Summary</h4>
                        <p><strong>Activities:</strong> {tripData.tripItems.length} selected</p>
                        <p><strong>Estimated Total:</strong> €{calculateTotalPrice().toFixed(2)}</p>
                    </div>

                    {submitError && (
                        <div className="export-error">
                            <p>{submitError}</p>
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="contact-form">
                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="fullName">Full Name *</label>
                                <input
                                    type="text"
                                    id="fullName"
                                    name="fullName"
                                    value={formData.fullName}
                                    onChange={handleInputChange}
                                    className={errors.fullName ? 'error' : ''}
                                    placeholder="John Doe"
                                />
                                {errors.fullName && <span className="error-message">{errors.fullName}</span>}
                            </div>

                            <div className="form-group">
                                <label htmlFor="email">Email Address *</label>
                                <input
                                    type="email"
                                    id="email"
                                    name="email"
                                    value={formData.email}
                                    onChange={handleInputChange}
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
                                    type="tel"
                                    id="phone"
                                    name="phone"
                                    value={formData.phone}
                                    onChange={handleInputChange}
                                    className={errors.phone ? 'error' : ''}
                                    placeholder="+1 234 567 8900"
                                />
                                {errors.phone && <span className="error-message">{errors.phone}</span>}
                            </div>

                            <div className="form-group">
                                <label htmlFor="numberOfTravelers">Number of Travelers *</label>
                                <input
                                    type="number"
                                    id="numberOfTravelers"
                                    name="numberOfTravelers"
                                    value={formData.numberOfTravelers}
                                    onChange={handleInputChange}
                                    min="1"
                                    max="20"
                                    className={errors.numberOfTravelers ? 'error' : ''}
                                />
                                {errors.numberOfTravelers &&
                                    <span className="error-message">{errors.numberOfTravelers}</span>}
                            </div>
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="startDate">Start Date *</label>
                                <input
                                    type="date"
                                    id="startDate"
                                    name="startDate"
                                    value={formData.startDate}
                                    onChange={handleInputChange}
                                    className={errors.startDate ? 'error' : ''}
                                    min={new Date().toISOString().split('T')[0]}
                                />
                                {errors.startDate && <span className="error-message">{errors.startDate}</span>}
                            </div>

                            <div className="form-group">
                                <label htmlFor="endDate">End Date *</label>
                                <input
                                    type="date"
                                    id="endDate"
                                    name="endDate"
                                    value={formData.endDate}
                                    onChange={handleInputChange}
                                    className={errors.endDate ? 'error' : ''}
                                    min={formData.startDate || new Date().toISOString().split('T')[0]}
                                />
                                {errors.endDate && <span className="error-message">{errors.endDate}</span>}
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="contactMethod">Preferred Contact Method *</label>
                            <select
                                id="contactMethod"
                                name="contactMethod"
                                value={formData.contactMethod}
                                onChange={handleInputChange}
                            >
                                <option value="email">Email</option>
                                <option value="phone">Phone</option>
                                <option value="whatsapp">WhatsApp</option>
                                <option value="both">Both</option>
                            </select>
                        </div>

                        <div className="form-group">
                            <label htmlFor="hearAboutUs">How did you hear about us?</label>
                            <select
                                id="hearAboutUs"
                                name="hearAboutUs"
                                value={formData.hearAboutUs}
                                onChange={handleInputChange}
                            >
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
                                id="specialRequirements"
                                name="specialRequirements"
                                value={formData.specialRequirements}
                                onChange={handleInputChange}
                                rows="3"
                                placeholder="Any special dietary requirements, accessibility needs, or other preferences..."
                            />
                        </div>
                    </form>
                </div>

                <div className="modal-footer">
                    <button
                        className="btn btn--secondary"
                        onClick={onClose}
                        disabled={isSubmitting}
                    >
                        Cancel
                    </button>
                    <button
                        className="btn btn--primary"
                        onClick={handleSubmit}
                        disabled={isSubmitting}
                    >
                        {isSubmitting ? 'Submitting...' : 'Submit Booking'}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default ContactForm;
