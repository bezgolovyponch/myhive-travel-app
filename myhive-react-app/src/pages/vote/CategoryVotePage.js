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

        if (nextIndex >= categories.length && updatedLikedIds.length === 0) {
            setCurrentIndex(0);
            setLikedCategoryIds([]);
            setError('You need to like at least one category. Starting over!');
            return;
        }

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
            localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
            localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
            navigate(`/vote/${session.shareToken}/activities`);
        } catch (e) {
            setError(e.message || 'Failed to create session. Please try again.');
            setSubmitting(false);
        }
    };

    if (loading) return <div style={{ padding: 40, textAlign: 'center' }}>Loading categories...</div>;
    if (error) return (
        <div style={{ padding: 40, textAlign: 'center', color: 'red' }}>
            <p>{error}</p>
            {error.includes('category') && (
                <button
                    onClick={() => { setError(null); setCurrentIndex(0); setLikedCategoryIds([]); }}
                    style={{ marginTop: 16, padding: '10px 24px', background: 'var(--brand, #6A1B9A)', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 600 }}
                >
                    Try again
                </button>
            )}
        </div>
    );

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
