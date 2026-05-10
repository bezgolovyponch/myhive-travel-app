import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import api from '../../services/api';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';

function CategoryVotePage() {
    const location = useLocation();
    const navigate = useNavigate();
    const { destinationId, destinationSlug, destinationName, voteSetup } = location.state || {};

    const [categories, setCategories] = useState([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [likedCategoryIds, setLikedCategoryIds] = useState([]);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!destinationId || !voteSetup) {
            navigate('/');
            return;
        }
        api.getCategoriesForDestination(destinationId)
            .then(cats => setCategories(cats.map(c => ({ id: c.id, name: c.name }))))
            .catch(() => setError('Failed to load categories'))
            .finally(() => setLoading(false));
    }, [destinationId]);

    const handleSwipe = async (direction, categoryId) => {
        const updatedLikedIds = direction === 'right'
            ? [...likedCategoryIds, categoryId]
            : likedCategoryIds;

        const nextIndex = currentIndex + 1;
        setLikedCategoryIds(updatedLikedIds);
        setCurrentIndex(nextIndex);

        if (nextIndex >= categories.length) {
            await finishAndCreateSession(updatedLikedIds);
        }
    };

    const finishAndCreateSession = async (finalLikedIds) => {
        if (submitting) return;
        setSubmitting(true);
        setError(null);
        try {
            const session = await voteApi.createSession({
                destinationId,
                initiatorEmail: voteSetup.email,
                numberOfTravelers: voteSetup.travelers,
                startDate: voteSetup.startDate,
                endDate: voteSetup.endDate,
                likedCategoryIds: finalLikedIds,
            });
            navigate(`/vote/${session.shareToken}/activities`, {
                state: { isInitiator: true },
            });
        } catch (e) {
            setError(e.message || 'Failed to create session. Please try again.');
            setSubmitting(false);
        }
    };

    if (loading) return <div style={{ padding: 40, textAlign: 'center' }}>Loading categories...</div>;
    if (error) return <div style={{ padding: 40, textAlign: 'center', color: 'red' }}>{error}</div>;

    return (
        <SwipeCard
            cards={categories}
            currentIndex={currentIndex}
            onSwipe={handleSwipe}
            title={`What interests you in ${destinationName || 'this destination'}?`}
            subtitle="Swipe right to like a category, left to skip"
        />
    );
}

export default CategoryVotePage;
