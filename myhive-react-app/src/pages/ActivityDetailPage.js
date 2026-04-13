import {useContext, useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import api from '../services/api';
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE, formatPrice} from '../utils/format';
import './ActivityDetailPage.css';

function ActivityDetailPage() {
    const {id} = useParams();
    const navigate = useNavigate();
    const {state, dispatch} = useContext(AppContext);
    const [activity, setActivity] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchActivity = async () => {
            try {
                setLoading(true);
                const data = await api.getActivity(id);
                setActivity(data);
            } catch (error) {
                console.error('Error fetching activity:', error);
            } finally {
                setLoading(false);
            }
        };
        fetchActivity();
    }, [id]);

    if (loading) {
        return (
            <div className="activity-detail-page">
                <div className="activity-detail-loading">Loading...</div>
            </div>
        );
    }

    if (!activity) {
        return (
            <div className="activity-detail-page">
                <div className="activity-detail-loading">Activity not found.</div>
            </div>
        );
    }

    const imageUrl = activity.imageUrl || DEFAULT_ACTIVITY_IMAGE;
    const title = activity.name || activity.title;
    const formattedPrice = formatPrice(activity.price);
    const duration = activity.duration ? `${activity.duration} min` : null;
    const category = capitalizeFirst(activity.category) || 'Activity';
    const isAdded = state.tripItems.some(item => item.id === activity.id);

    const handleAddToTrip = () => {
        dispatch({type: 'ADD_TO_TRIP', activity});
    };

    return (
        <div className="activity-detail-page">
            <button className="activity-detail-back" onClick={() => navigate(-1)}>
                &larr; Back
            </button>

            <div className="activity-detail-container">
                <div className="activity-detail-image-section">
                    <img src={imageUrl} alt={title} className="activity-detail-image"/>
                </div>

                <div className="activity-detail-info">
                    <span className="activity-category">{category}</span>
                    <h1 className="activity-detail-title">{title}</h1>

                    <div className="activity-detail-meta">
                        <span className="activity-detail-price">{formattedPrice}</span>
                        {duration && <span className="activity-detail-duration">{duration}</span>}
                    </div>

                    <p className="activity-detail-description">{activity.description}</p>

                    <button
                        className="add-to-trip-btn activity-detail-add-btn"
                        onClick={handleAddToTrip}
                        disabled={isAdded}
                    >
                        {isAdded ? 'Added to Trip' : 'Add to Trip'}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default ActivityDetailPage;
