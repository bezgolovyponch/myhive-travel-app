import {Link, useParams} from 'react-router-dom';

const posts = {
    1: {
        title: 'Top 5 Group Travel Destinations for 2026',
        date: 'March 15, 2026',
        category: 'Destinations',
        image: 'https://images.unsplash.com/photo-1519677100203-a0e668c92439?w=1200&h=500&fit=crop',
        content: [
            'Planning a group trip can feel overwhelming, but choosing the right destination makes all the difference. We\'ve rounded up the five best spots for group adventures in 2026, based on activity variety, affordability, and group-friendliness.',
            'Prague continues to reign as a top pick for groups. With its stunning architecture, vibrant nightlife, and incredibly affordable prices, it offers something for every type of traveler. Walking tours through the Old Town, river cruises on the Vltava, and pub crawls through centuries-old beer halls make it ideal for groups of any size.',
            'Tenerife brings volcanic landscapes, beach parties, and year-round sunshine. Groups love the mix of adventure activities like hiking Mount Teide and relaxing on black sand beaches. The island\'s diverse microclimates mean you can find the perfect weather no matter when you visit.',
            'Bali remains the ultimate group retreat destination. From shared villas with private pools to group surfing lessons and temple tours, the Island of the Gods delivers unforgettable shared experiences at prices that won\'t break the bank.',
            'Dubai offers luxury group experiences like no other city. Desert safaris, rooftop dining, and water parks create the perfect blend of relaxation and adventure. Group packages at many attractions make it more affordable than you\'d think.',
            'New York City rounds out our list with its endless entertainment options. Broadway shows, food tours through diverse neighborhoods, and iconic landmarks ensure every member of your group finds something they love.',
        ],
    },
    2: {
        title: 'How to Plan a Stress-Free Group Trip',
        date: 'March 8, 2026',
        category: 'Tips',
        image: 'https://images.unsplash.com/photo-1539635278303-d4002c07eae3?w=1200&h=500&fit=crop',
        content: [
            'Group travel should be about making memories, not managing logistics. Yet too often, the planning process becomes a source of friction. Here\'s how to keep things smooth from start to finish.',
            'Start by establishing a shared budget early. Money is the number one source of group travel conflict. Use a shared document or app where everyone can input their comfort level, then plan activities that fit within the group\'s range.',
            'Designate a trip leader, but distribute responsibilities. One person shouldn\'t carry the weight of all decisions. Assign roles: someone handles accommodation research, another looks into activities, and another manages transportation.',
            'Build in free time. Not every minute needs to be scheduled. Some of the best group travel moments happen spontaneously. Plan key activities together but leave gaps for people to explore on their own or in smaller groups.',
            'Use technology to your advantage. Tools like MyHive\'s Trip Builder let everyone browse and vote on activities, making group decision-making democratic and fun rather than chaotic.',
            'Finally, set expectations early about communication. Create a single group chat, agree on response times for decisions, and establish a deadline for final commitments. Clear communication prevents last-minute surprises.',
        ],
    },
    3: {
        title: 'Why AI is Changing the Way We Travel Together',
        date: 'February 28, 2026',
        category: 'Technology',
        image: 'https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=1200&h=500&fit=crop',
        content: [
            'Artificial intelligence is no longer just a buzzword in travel — it\'s fundamentally reshaping how groups plan, book, and experience trips together.',
            'Traditional group travel planning involved endless back-and-forth messages, spreadsheets for tracking preferences, and compromise after compromise. AI changes this by analyzing everyone\'s preferences simultaneously and suggesting itineraries that maximize group satisfaction.',
            'Smart recommendation engines can now understand that while half the group wants adventure and the other half wants relaxation, a destination like Bali offers both. These algorithms consider budget constraints, travel dates, and even dietary preferences to create personalized group experiences.',
            'Real-time pricing optimization is another game-changer. AI monitors flight and accommodation prices across hundreds of platforms, alerting groups to the best time to book and finding deals that fit everyone\'s budget.',
            'Language barriers are dissolving too. AI-powered translation tools mean groups can confidently explore destinations where they don\'t speak the local language, opening up a world of off-the-beaten-path experiences.',
            'At MyHive, we\'re building the future of multi-traveler experiences. Our AI-powered trip builder doesn\'t just find activities — it creates cohesive itineraries that bring groups closer together while respecting individual preferences.',
        ],
    },
    4: {
        title: 'Bali on a Budget: A Group Travel Guide',
        date: 'February 20, 2026',
        category: 'Guides',
        image: 'https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=1200&h=500&fit=crop',
        content: [
            'Bali has a reputation as a luxury destination, but savvy groups can experience the magic of the Island of the Gods without spending a fortune. Here\'s your complete budget guide.',
            'Accommodation is where groups save the most. Instead of individual hotel rooms, rent a shared villa. A stunning 4-bedroom villa with a private pool can cost as little as €30 per person per night when split among a group of 8. Areas like Canggu and Ubud offer the best value.',
            'Eat like a local and your food budget shrinks dramatically. Warungs (local restaurants) serve incredible Indonesian dishes for €2-3 per meal. Save the fancy restaurants for one or two special group dinners, and cook group breakfasts at your villa.',
            'Transportation in Bali is affordable when shared. Hire a private driver for the day for around €30-40, split among your group. This gives you flexibility to explore temples, rice terraces, and beaches at your own pace.',
            'Many of Bali\'s best experiences are free or nearly free. Watch the sunset at Tanah Lot temple, explore the Tegallalang rice terraces, walk through the Ubud Monkey Forest, or simply spend the day at one of the island\'s beautiful beaches.',
            'For activities, look for group discounts. White water rafting, snorkeling trips, and cooking classes often offer lower per-person rates for groups of 6 or more. Book through your accommodation for the best deals.',
        ],
    },
    5: {
        title: 'The Rise of Multi-Traveler Experiences',
        date: 'February 12, 2026',
        category: 'Trends',
        image: 'https://images.unsplash.com/photo-1530789253388-582c481c54b0?w=1200&h=500&fit=crop',
        content: [
            'For years, solo travel dominated the conversation. Instagram feeds were filled with lone adventurers at exotic destinations, and "finding yourself" through solo exploration became a cultural mantra. But the tide is turning.',
            'Post-pandemic, people are craving connection more than ever. The rise of remote work means friend groups are scattered across cities and countries, making intentional group trips more meaningful — and more necessary — than before.',
            'Multi-traveler experiences go beyond traditional group tours. They\'re customizable, flexible adventures where the group shapes the itinerary rather than following a rigid schedule. Think shared villa stays with curated activity menus, not bus tours with numbered stickers.',
            'The economics make sense too. Sharing accommodation, transportation, and group activity rates means everyone gets a better experience for less money. A luxury villa split eight ways often costs less than a mid-range hotel room.',
            'Social media is evolving to reflect this shift. Shared photo albums, group travel accounts, and collaborative content creation are becoming the new norm. The best travel stories aren\'t solo anymore — they\'re collective.',
            'This is exactly why we built MyHive. The tools for solo travel planning are everywhere, but platforms designed specifically for group coordination were virtually nonexistent. We\'re changing that.',
        ],
    },
    6: {
        title: 'Weekend Getaway Ideas for Large Groups',
        date: 'February 5, 2026',
        category: 'Destinations',
        image: 'https://images.unsplash.com/photo-1506197603052-3cc9c3a201bd?w=1200&h=500&fit=crop',
        content: [
            'Planning a weekend escape for 10 or more people presents unique challenges, but the payoff is worth it. Here are our favorite destinations and formats for large group getaways.',
            'Country house rentals are the gold standard for large group weekends. Platforms now offer properties that sleep 15-20 people, complete with games rooms, hot tubs, and large kitchens. The shared living spaces create natural gathering points while private bedrooms offer retreat.',
            'Activity-focused weekends work brilliantly for large groups. Book a group surfing weekend, a wine tasting tour, or a cooking retreat. Having a shared activity gives the weekend structure without feeling overly planned.',
            'City breaks can work for large groups if you plan smart. Book connected rooms or apartments in the same building, choose a neighborhood with plenty of restaurant options, and plan one or two group activities while leaving time for people to explore in smaller clusters.',
            'Festival weekends are perfect for large groups. Music festivals, food festivals, and cultural events provide built-in entertainment and a shared experience that bonds the group. Book accommodation early and establish a meeting point for easy regrouping.',
            'Whatever format you choose, the key is balance. Large groups need a mix of together time and freedom. Plan the must-do moments as a full group, but let natural sub-groups form for meals and downtime. The best large group trips feel effortless — even when they take careful planning behind the scenes.',
        ],
    },
};

function BlogPostPage() {
    const {id} = useParams();
    const post = posts[id];

    if (!post) {
        return (
            <div className="blog-post-page">
                <div className="blog-post-container">
                    <h1>Post not found</h1>
                    <Link to="/blog" className="btn btn--primary">Back to Blog</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="blog-post-page">
            <div className="blog-post-hero" style={{backgroundImage: `url(${post.image})`}}>
                <div className="blog-post-hero-overlay">
                    <span className="blog-post-category">{post.category}</span>
                    <h1>{post.title}</h1>
                    <span className="blog-post-date">{post.date}</span>
                </div>
            </div>

            <article className="blog-post-container">
                {post.content.map((paragraph, index) => (
                    <p key={index}>{paragraph}</p>
                ))}

                <div className="blog-post-back">
                    <Link to="/blog" className="btn btn--primary">Back to Blog</Link>
                </div>
            </article>
        </div>
    );
}

export default BlogPostPage;
