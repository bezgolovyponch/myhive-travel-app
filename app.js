// MyHive Travel Application JavaScript

// Application Data
const appData = {
    destinations: [
        {
            id: "prague",
            name: "Prague",
            image: "https://images.unsplash.com/photo-1541849546-216549ae216d?w=400&h=300&fit=crop",
            clickable: true,
            badge: "Popular",
            badgeColor: "#4CAF50",
            description: "Medieval charm meets epic nightlife"
        },
        {
            id: "tenerife",
            name: "Tenerife",
            image: "https://images.unsplash.com/photo-1594401708939-49f49fdf596a?w=400&h=300&fit=crop",
            clickable: true,
            badge: "Hot Deal",
            badgeColor: "#FF5722",
            description: "Volcanic adventures and beach parties"
        },
        {
            id: "budapest",
            name: "Budapest",
            image: "https://images.unsplash.com/photo-1500078974918-738828bc0422?w=400&h=300&fit=crop",
            clickable: false,
            badge: "Coming Soon",
            badgeColor: "#999",
            description: "Thermal baths and ruin bars"
        },
        {
            id: "barcelona",
            name: "Barcelona",
            image: "https://images.unsplash.com/photo-1539037116277-4db20889f2d4?w=400&h=300&fit=crop",
            clickable: false,
            badge: "Coming Soon",
            badgeColor: "#999",
            description: "Gaudi architecture and beach clubs"
        },
        {
            id: "athens",
            name: "Athens",
            image: "https://images.unsplash.com/photo-1555993539-1732b0258235?w=400&h=300&fit=crop",
            clickable: false,
            badge: "Coming Soon",
            badgeColor: "#999",
            description: "Ancient history and rooftop bars"
        }
    ],
    tenerifeActivities: [
        {
            id: "sunset-boat-party",
            title: "Sunset Boat Party",
            category: "nightlife",
            description: "3-hour catamaran cruise with live DJ and open bar",
            price: "€69 pp",
            image: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=400&h=300&fit=crop"
        },
        {
            id: "teide-tour",
            title: "Teide National Park Tour",
            category: "adventure",
            description: "Explore volcanic landscapes with expert guide",
            price: "€55 pp",
            image: "https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=400&h=300&fit=crop"
        },
        {
            id: "beach-club-access",
            title: "VIP Beach Club Access",
            category: "daytime",
            description: "Exclusive access to premium beach club with drinks",
            price: "€45 pp",
            image: "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=400&h=300&fit=crop"
        },
        {
            id: "pub-crawl",
            title: "Epic Pub Crawl",
            category: "nightlife",
            description: "Visit 5 top bars with shots and club entry",
            price: "€35 pp",
            image: "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=400&h=300&fit=crop"
        },
        {
            id: "jet-ski-adventure",
            title: "Jet Ski Adventure",
            category: "adventure",
            description: "2-hour guided jet ski tour around the coast",
            price: "€85 pp",
            image: "https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=400&h=300&fit=crop"
        },
        {
            id: "spa-session",
            title: "Luxury Spa Session",
            category: "daytime",
            description: "3-hour spa package with massage and treatments",
            price: "€120 pp",
            image: "https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=400&h=300&fit=crop"
        }
    ],
    tenerifePackages: [
        {
            id: "action-stag-weekend",
            title: "Action Stag Weekend",
            description: "2 days of adventure and nightlife with pub crawl, jet ski, and VIP club entry",
            activities: ["pub-crawl", "jet-ski-adventure", "sunset-boat-party"],
            price: "€189 pp",
            image: "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=400&h=300&fit=crop"
        },
        {
            id: "beach-bliss-package",
            title: "Beach Bliss Package",
            description: "Relaxed group escape with sunset boat party, beach club, and spa session",
            activities: ["sunset-boat-party", "beach-club-access", "spa-session"],
            price: "€234 pp",
            image: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400&h=300&fit=crop"
        },
        {
            id: "adventure-seekers",
            title: "Adventure Seekers",
            description: "Thrill-packed weekend with Teide tour, jet ski, and beach club access",
            activities: ["teide-tour", "jet-ski-adventure", "beach-club-access"],
            price: "€205 pp",
            image: "https://images.unsplash.com/photo-1551698618-1dfe5d97d256?w=400&h=300&fit=crop"
        }
    ]
};

// Application State
let appState = {
    currentPath: '/',
    currentTab: 'activities',
    currentFilter: 'all',
    tripItems: [],
    chatOpen: false,
    chatMessages: [
        { sender: 'ai', text: 'Hi! I\'m your AI travel assistant. What type of trip are you looking for?' }
    ],
    autoEngaged: false
};

// Core App Class
class MyHiveApp {
    constructor() {
        this.init();
    }

    init() {
        console.log('Initializing MyHive application...');
        
        try {
        // Always control scroll ourselves
        if ('scrollRestoration' in history) {
            history.scrollRestoration = 'manual';
        }
            // Initialize router
            this.initRouter();
            
            // Render initial content
            this.renderDestinations();
            this.renderActivities();
            this.renderPackages();
            this.renderTripBuilder();
            this.renderChatMessages();
            
            // Setup event listeners
            this.setupEventListeners();
            
            // Auto-engage chat after 3 seconds on homepage
            setTimeout(() => {
                if (appState.currentPath === '/' && !appState.chatOpen && !appState.autoEngaged) {
                    this.addChatMessage('ai', 'Looking for an epic weekend? Tenerife offers amazing volcanic adventures and beach parties year-round!');
                    appState.autoEngaged = true;
                }
            }, 3000);
            
            console.log('MyHive application initialized successfully');
            
        } catch (error) {
            console.error('Error initializing application:', error);
        }
    }

    // Router functionality
    initRouter() {
        console.log('Initializing router...');
        
        // Handle initial load
        const path = window.location.pathname || '/';
        this.navigateTo(path, false);
        
        // Handle browser back/forward buttons
        window.addEventListener('popstate', (event) => {
            const path = event.state ? event.state.path : '/';
            this.navigateTo(path, false);
        });
    }

    navigateTo(path, addToHistory = true) {
        console.log('Navigating to:', path);
    // Always start new views at the top
    window.scrollTo(0, 0);
        
        appState.currentPath = path;
        
        if (addToHistory) {
            history.pushState({ path }, '', path);
        }
        
        // Hide all pages
        const pages = document.querySelectorAll('.page');
        pages.forEach(page => {
            page.style.display = 'none';
        });
        
        // Show breadcrumbs for destination pages
        const breadcrumbs = document.getElementById('breadcrumbs');
        
        if (path === '/') {
            // Homepage
            const homepage = document.getElementById('homepage');
            if (homepage) {
                homepage.style.display = 'block';
            }
            if (breadcrumbs) {
                breadcrumbs.style.display = 'none';
            }
        } else if (path.startsWith('/tenerife')) {
            // Tenerife destination page
            const tenerifePage = document.getElementById('tenerife-page');
            if (tenerifePage) {
                tenerifePage.style.display = 'block';
            }
            if (breadcrumbs) {
                breadcrumbs.style.display = 'block';
            }
            
            // Determine which tab to show
            if (path === '/tenerife/packages') {
                this.switchTab('packages');
            } else if (path === '/tenerife/trip-builder') {
                this.switchTab('trip-builder');
            } else {
                this.switchTab('activities');
            }
            
            this.updateBreadcrumbs();
        } else {
            // Default to homepage
            const homepage = document.getElementById('homepage');
            if (homepage) {
                homepage.style.display = 'block';
            }
            if (breadcrumbs) {
                breadcrumbs.style.display = 'none';
            }
        }
        
        console.log('Navigation complete to:', path);
    }

    updateBreadcrumbs() {
        const breadcrumbsContent = document.querySelector('.breadcrumbs-content');
        if (!breadcrumbsContent) return;
        
        const currentPath = appState.currentPath;
        
        let breadcrumbsHtml = '<a href="#" class="breadcrumb-item" data-navigate="/">Home</a>';
        breadcrumbsHtml += '<span class="breadcrumb-separator">></span>';
        breadcrumbsHtml += '<a href="#" class="breadcrumb-item" data-navigate="/tenerife/activities">Tenerife</a>';
        
        if (currentPath.includes('/packages')) {
            breadcrumbsHtml += '<span class="breadcrumb-separator">></span>';
            breadcrumbsHtml += '<span class="breadcrumb-item current">Packages</span>';
        } else if (currentPath.includes('/trip-builder')) {
            breadcrumbsHtml += '<span class="breadcrumb-separator">></span>';
            breadcrumbsHtml += '<span class="breadcrumb-item current">Trip Builder</span>';
        } else {
            breadcrumbsHtml += '<span class="breadcrumb-separator">></span>';
            breadcrumbsHtml += '<span class="breadcrumb-item current">Activities</span>';
        }
        
        breadcrumbsContent.innerHTML = breadcrumbsHtml;
    }

    // Tab switching functionality
    switchTab(tabName) {
        console.log('Switching to tab:', tabName);
        appState.currentTab = tabName;
        
        // Update URL
        const newPath = `/tenerife/${tabName}`;
        if (appState.currentPath !== newPath) {
            history.pushState({ path: newPath }, '', newPath);
            appState.currentPath = newPath;
        }
        
        // Update tab buttons
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        const activeTabBtn = document.querySelector(`[data-tab="${tabName}"]`);
        if (activeTabBtn) {
            activeTabBtn.classList.add('active');
        }
        
        // Hide all tab content
        document.querySelectorAll('.tab-content').forEach(content => {
            content.style.display = 'none';
        });
        
        // Show selected tab content
        const tabContent = document.getElementById(`${tabName}-tab`);
        if (tabContent) {
            tabContent.style.display = 'block';
        }
        
        // Update breadcrumbs
        this.updateBreadcrumbs();
        
        // Update chat context
        this.updateChatContext(tabName);
    }

    // Render functions
    renderDestinations() {
        const grid = document.getElementById('destinations-grid');
        if (!grid) return;
        
        grid.innerHTML = appData.destinations.map(dest => {
            const badgeClass = dest.badge === 'Popular' ? 'badge-popular' : 
                             dest.badge === 'Hot Deal' ? 'badge-hot-deal' : 'badge-coming-soon';
            
            return `
                <div class="destination-card ${dest.clickable ? '' : 'disabled'}" 
                     data-destination="${dest.id}">
                    <img src="${dest.image}" alt="${dest.name}" class="destination-image" 
                         loading="lazy" onerror="this.style.display='none'" />
                    <div class="destination-badge ${badgeClass}">${dest.badge}</div>
                    <div class="destination-content">
                        <h3 class="destination-name">${dest.name}</h3>
                        <p class="destination-description">${dest.description}</p>
                    </div>
                </div>
            `;
        }).join('');
        
        console.log('Destinations rendered');
    }

    renderActivities(filter = 'all') {
        const grid = document.getElementById('activities-grid');
        if (!grid) return;
        
        const filteredActivities = filter === 'all' ? 
            appData.tenerifeActivities : 
            appData.tenerifeActivities.filter(activity => activity.category === filter);
        
        grid.innerHTML = filteredActivities.map(activity => {
            const isAdded = appState.tripItems.some(item => item.id === activity.id);
            
            return `
                <div class="activity-card">
                    <img src="${activity.image}" alt="${activity.title}" class="activity-image" 
                         loading="lazy" onerror="this.style.display='none'" />
                    <div class="activity-content">
                        <span class="activity-category">${activity.category.charAt(0).toUpperCase() + activity.category.slice(1)}</span>
                        <h3 class="activity-title">${activity.title}</h3>
                        <p class="activity-description">${activity.description}</p>
                        <div class="activity-footer">
                            <span class="activity-price">${activity.price}</span>
                            <button class="add-to-trip-btn" 
                                    data-activity="${activity.id}" 
                                    ${isAdded ? 'disabled' : ''}>
                                ${isAdded ? 'Added' : 'Add to Trip'}
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
        
        console.log('Activities rendered, filter:', filter);
    }

    renderPackages() {
        const grid = document.getElementById('packages-grid');
        if (!grid) return;
        
        grid.innerHTML = appData.tenerifePackages.map(pkg => {
            const activityNames = pkg.activities.map(actId => {
                const activity = appData.tenerifeActivities.find(a => a.id === actId);
                return activity ? activity.title : actId;
            });
            
            return `
                <div class="package-card">
                    <img src="${pkg.image}" alt="${pkg.title}" class="package-image" 
                         loading="lazy" onerror="this.style.display='none'" />
                    <div class="package-content">
                        <h3 class="package-title">${pkg.title}</h3>
                        <p class="package-description">${pkg.description}</p>
                        <div class="package-activities">
                            ${activityNames.map(name => 
                                `<span class="package-activity-tag">${name}</span>`
                            ).join('')}
                        </div>
                        <div class="package-footer">
                            <span class="package-price">${pkg.price}</span>
                            <button class="select-package-btn" data-package="${pkg.id}">
                                Select Package
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
        
        console.log('Packages rendered');
    }

    renderTripBuilder() {
        this.renderItinerary();
        this.renderBrowseActivities();
    }

    renderItinerary() {
        const itineraryList = document.getElementById('itinerary-list');
        const itineraryCount = document.getElementById('itinerary-count');
        const confirmBtn = document.getElementById('confirm-btn');
        
        if (!itineraryList) return;
        
        if (appState.tripItems.length === 0) {
            itineraryList.innerHTML = `
                <div class="empty-state">
                    <p>Start building your trip by adding activities!</p>
                </div>
            `;
            if (confirmBtn) {
                confirmBtn.style.display = 'none';
            }
        } else {
            itineraryList.innerHTML = appState.tripItems.map(item => `
                <div class="itinerary-item">
                    <img src="${item.image}" alt="${item.title}" class="itinerary-item-image" 
                         loading="lazy" onerror="this.style.display='none'" />
                    <div class="itinerary-item-content">
                        <div class="itinerary-item-title">${item.title}</div>
                        <div class="itinerary-item-price">${item.price}</div>
                    </div>
                    <button class="remove-item-btn" data-remove="${item.id}">×</button>
                </div>
            `).join('');
            
            if (confirmBtn) {
                confirmBtn.style.display = 'block';
            }
        }
        
        if (itineraryCount) {
            itineraryCount.textContent = `${appState.tripItems.length} ${appState.tripItems.length === 1 ? 'activity' : 'activities'} selected`;
        }
    }

    renderBrowseActivities(filter = 'all') {
        const browseContainer = document.getElementById('browse-activities');
        if (!browseContainer) return;
        
        const filteredActivities = filter === 'all' ? 
            appData.tenerifeActivities : 
            appData.tenerifeActivities.filter(activity => activity.category === filter);
        
        browseContainer.innerHTML = filteredActivities.map(activity => {
            const isAdded = appState.tripItems.some(item => item.id === activity.id);
            
            return `
                <div class="browse-activity-item">
                    <img src="${activity.image}" alt="${activity.title}" class="browse-activity-image" 
                         loading="lazy" onerror="this.style.display='none'" />
                    <div class="browse-activity-content">
                        <div class="browse-activity-title">${activity.title}</div>
                        <div class="browse-activity-price">${activity.price}</div>
                    </div>
                    <button class="browse-add-btn" 
                            data-activity="${activity.id}" 
                            ${isAdded ? 'disabled' : ''}>
                        ${isAdded ? 'Added' : 'Add'}
                    </button>
                </div>
            `;
        }).join('');
    }

    // Event handlers
    handleDestinationClick(destinationId) {
        console.log('Destination clicked:', destinationId);
        
        const destination = appData.destinations.find(d => d.id === destinationId);
        if (!destination) return;
        
        if (destination.clickable) {
            if (destinationId === 'tenerife') {
                this.navigateTo('/tenerife/activities');
            } else if (destinationId === 'prague') {
                alert('🎉 Prague is coming soon! We\'re working on amazing experiences there.');
            }
            
            // Add automatic chat response
            setTimeout(() => {
                let response = '';
                if (destinationId === 'prague') {
                    response = 'Great choice! Prague offers incredible medieval architecture, world-famous beer culture, and legendary nightlife. We\'ll have bookings available soon!';
                } else if (destinationId === 'tenerife') {
                    response = 'Excellent pick! Tenerife combines stunning volcanic landscapes with amazing beaches and vibrant nightlife. Let me know if you need help choosing activities!';
                }
                
                if (response) {
                    this.addChatMessage('ai', response);
                }
            }, 500);
        } else {
            alert('⏳ This destination is coming soon!\n\nWe\'re working hard to bring you amazing experiences here. Stay tuned!');
        }
    }

    filterActivities(category) {
        console.log('Filtering activities by:', category);
        appState.currentFilter = category;
        
        // Update filter buttons
        document.querySelectorAll('#activities-tab .filter-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        const activeFilter = document.querySelector(`#activities-tab [data-category="${category}"]`);
        if (activeFilter) {
            activeFilter.classList.add('active');
        }
        
        this.renderActivities(category);
    }

    filterBrowseActivities(category) {
        console.log('Filtering browse activities by:', category);
        
        // Update filter buttons
        document.querySelectorAll('.browse-filters .filter-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        const activeFilter = document.querySelector(`.browse-filters [data-category="${category}"]`);
        if (activeFilter) {
            activeFilter.classList.add('active');
        }
        
        this.renderBrowseActivities(category);
    }

    addToTrip(activityId) {
        console.log('Adding to trip:', activityId);
        
        const activity = appData.tenerifeActivities.find(a => a.id === activityId);
        if (activity && !appState.tripItems.some(item => item.id === activityId)) {
            appState.tripItems.push(activity);
            this.renderActivities(appState.currentFilter);
            this.renderTripBuilder();
            
            // Add chat message
            this.addChatMessage('ai', `Great choice! I've added "${activity.title}" to your trip. ${this.getActivitySuggestion(activity)}`);
        }
    }

    removeFromTrip(activityId) {
        console.log('Removing from trip:', activityId);
        
        const activity = appData.tenerifeActivities.find(a => a.id === activityId);
        appState.tripItems = appState.tripItems.filter(item => item.id !== activityId);
        this.renderActivities(appState.currentFilter);
        this.renderTripBuilder();
        
        if (activity) {
            this.addChatMessage('ai', `I've removed "${activity.title}" from your trip. Would you like me to suggest something similar?`);
        }
    }

    selectPackage(packageId) {
        console.log('Selecting package:', packageId);
        
        const pkg = appData.tenerifePackages.find(p => p.id === packageId);
        if (pkg) {
            // Add all activities from the package
            pkg.activities.forEach(actId => {
                const activity = appData.tenerifeActivities.find(a => a.id === actId);
                if (activity && !appState.tripItems.some(item => item.id === actId)) {
                    appState.tripItems.push(activity);
                }
            });
            
            // Switch to trip builder tab
            this.switchTab('trip-builder');
            this.renderTripBuilder();
            
            // Add chat message
            this.addChatMessage('ai', `Perfect! I've added the "${pkg.title}" package to your trip builder. You can now customize it by adding or removing activities. Want me to suggest any modifications?`);
        }
    }

    getActivitySuggestion(activity) {
        const suggestions = {
            'nightlife': 'This pairs perfectly with other nightlife activities! Consider adding a pub crawl too.',
            'adventure': 'Adventure lover! You might also enjoy our jet ski tour or Teide National Park visit.',
            'daytime': 'Great for relaxation! Our spa session would complement this beautifully.'
        };
        
        return suggestions[activity.category] || 'This looks like a great addition to your trip!';
    }

    proceedToConfirmation() {
        alert(`🎉 Proceeding to confirmation!\n\nYour trip includes ${appState.tripItems.length} activities:\n${appState.tripItems.map(item => `• ${item.title}`).join('\n')}\n\nThis would normally take you to the booking confirmation page.`);
    }

    openTripBuilder() {
        console.log('Opening trip builder, items:', appState.tripItems.length);
        
        const modal = document.getElementById('trip-builder-modal');
        if (appState.tripItems.length > 0) {
            // If user has items, go to trip builder tab
            this.navigateTo('/tenerife/trip-builder');
        } else {
            // Show empty state modal
            if (modal) {
                modal.classList.remove('hidden');
            }
        }
    }

    closeTripBuilderModal() {
        const modal = document.getElementById('trip-builder-modal');
        if (modal) {
            modal.classList.add('hidden');
        }
    }

    // Chat functionality
    toggleChat() {
        console.log('Toggling chat, current state:', appState.chatOpen);
        
        appState.chatOpen = !appState.chatOpen;
        const chatPanel = document.getElementById('chat-panel');
        
        if (!chatPanel) {
            console.error('Chat panel not found');
            return;
        }
        
        if (appState.chatOpen) {
            chatPanel.classList.add('open');
            setTimeout(() => {
                const input = document.getElementById('chat-input');
                if (input) {
                    input.focus();
                }
            }, 300);
        } else {
            chatPanel.classList.remove('open');
        }
    }

    addChatMessage(sender, text) {
        console.log('Adding chat message:', sender, text);
        appState.chatMessages.push({ sender, text });
        this.renderChatMessages();
        this.scrollChatToBottom();
    }

    renderChatMessages() {
        const messagesContainer = document.getElementById('chat-messages');
        if (!messagesContainer) return;
        
        messagesContainer.innerHTML = appState.chatMessages.map(msg => `
            <div class="chat-message ${msg.sender}">
                <div class="chat-avatar">${msg.sender === 'ai' ? 'AI' : 'You'}</div>
                <div class="chat-bubble">${msg.text}</div>
            </div>
        `).join('');
    }

    scrollChatToBottom() {
        setTimeout(() => {
            const chatMessages = document.getElementById('chat-messages');
            if (chatMessages) {
                chatMessages.scrollTop = chatMessages.scrollHeight;
            }
        }, 100);
    }

    updateChatContext(tab) {
        const contextMessages = {
            'activities': 'I can help you choose the perfect activities! Looking for nightlife, adventure, or daytime relaxation?',
            'packages': 'These pre-built packages are great starting points! Want to customize any of them?',
            'trip-builder': 'This is where your perfect trip comes together! Need help balancing your activities or finding something missing?'
        };
        
        // Only add context message if chat is open and user hasn't been too active
        if (appState.chatOpen && appState.chatMessages.length < 5) {
            setTimeout(() => {
                this.addChatMessage('ai', contextMessages[tab]);
            }, 1000);
        }
    }

    handleChatInput(event) {
        if (event.key === 'Enter' && event.target.value.trim()) {
            const userMessage = event.target.value.trim();
            const userMessageLower = userMessage.toLowerCase();
            
            this.addChatMessage('user', userMessage);
            event.target.value = '';
            
            // Generate contextual AI response
            let aiResponse = "I can help you plan the perfect Tenerife getaway! What interests you most?";
            
            if (userMessageLower.includes('party') || userMessageLower.includes('nightlife') || userMessageLower.includes('club')) {
                aiResponse = "For epic nightlife, try our Sunset Boat Party and Epic Pub Crawl! They're perfect for groups and offer amazing experiences with locals and other travelers.";
            } else if (userMessageLower.includes('adventure') || userMessageLower.includes('active') || userMessageLower.includes('volcano')) {
                aiResponse = "Adventure awaits! The Teide National Park Tour showcases incredible volcanic landscapes, and our Jet Ski Adventure lets you explore the stunning coastline from the water.";
            } else if (userMessageLower.includes('beach') || userMessageLower.includes('relax') || userMessageLower.includes('spa')) {
                aiResponse = "Perfect for relaxation! Our VIP Beach Club Access gets you into exclusive venues, and the Luxury Spa Session is ideal for unwinding after your adventures.";
            } else if (userMessageLower.includes('package') || userMessageLower.includes('deal')) {
                aiResponse = "Our packages offer great value! The Action Stag Weekend combines adventure and nightlife, while Beach Bliss is perfect for a more relaxed vibe. Both can be customized!";
            } else if (userMessageLower.includes('suggest') || userMessageLower.includes('recommend')) {
                const currentTab = appState.currentTab;
                if (currentTab === 'activities') {
                    aiResponse = "Based on what's popular, I'd recommend starting with the Sunset Boat Party - it's a great way to meet people and see the island from the water!";
                } else if (currentTab === 'packages') {
                    aiResponse = "The Beach Bliss Package is very popular - it has a perfect mix of party and relaxation. You can always customize it in the Trip Builder!";
                } else {
                    aiResponse = `You have ${appState.tripItems.length} activities selected. ${appState.tripItems.length < 2 ? 'Consider adding more for a full experience!' : 'This looks like a great balanced trip!'}`;
                }
            } else if (userMessageLower.includes('price') || userMessageLower.includes('cost') || userMessageLower.includes('budget')) {
                aiResponse = "Great value options include the Epic Pub Crawl (€35 pp) and VIP Beach Club Access (€45 pp). Our packages offer even better deals when you book multiple activities together!";
            } else if (userMessageLower.includes('group') || userMessageLower.includes('friends')) {
                aiResponse = "Perfect for groups! The Sunset Boat Party and Epic Pub Crawl are especially great for meeting other travelers. All our activities are group-friendly!";
            } else if (userMessageLower.includes('hi') || userMessageLower.includes('hello') || userMessageLower.includes('help')) {
                aiResponse = "Hello! I'm here to help you create the perfect Tenerife experience. Are you looking for adventure, nightlife, relaxation, or a mix of everything?";
            }
            
            setTimeout(() => {
                this.addChatMessage('ai', aiResponse);
            }, 1000);
        }
    }

    setupEventListeners() {
        console.log('Setting up event listeners...');
        
        // Chat functionality
        const chatTriggerBtn = document.getElementById('chat-trigger-btn');
        const chatCloseBtn = document.getElementById('chat-close-btn');
        const chatInput = document.getElementById('chat-input');
        
        if (chatTriggerBtn) {
            chatTriggerBtn.addEventListener('click', () => this.toggleChat());
        }
        
        if (chatCloseBtn) {
            chatCloseBtn.addEventListener('click', () => this.toggleChat());
        }
        
        if (chatInput) {
            chatInput.addEventListener('keypress', (e) => this.handleChatInput(e));
        }
        
        // Trip builder modal
        const tripBuilderBtn = document.getElementById('trip-builder-btn');
        const modalCloseBtn = document.getElementById('modal-close-btn');
        const browseDestinationsBtn = document.getElementById('browse-destinations-btn');
        const confirmBtn = document.getElementById('confirm-btn');
        
        if (tripBuilderBtn) {
            tripBuilderBtn.addEventListener('click', () => this.openTripBuilder());
        }
        
        if (modalCloseBtn) {
            modalCloseBtn.addEventListener('click', () => this.closeTripBuilderModal());
        }
        
        if (browseDestinationsBtn) {
            browseDestinationsBtn.addEventListener('click', () => {
                this.navigateTo('/');
                this.closeTripBuilderModal();
            });
        }
        
        if (confirmBtn) {
            confirmBtn.addEventListener('click', () => this.proceedToConfirmation());
        }
        
        // Event delegation for dynamically generated content
        document.addEventListener('click', (e) => {
            // Navigation links
            if (e.target.hasAttribute('data-navigate')) {
                e.preventDefault();
                const path = e.target.getAttribute('data-navigate');
                this.navigateTo(path);
            }
            
            // Destination cards
            if (e.target.closest('[data-destination]')) {
                const destinationId = e.target.closest('[data-destination]').getAttribute('data-destination');
                this.handleDestinationClick(destinationId);
            }
            
            // Tab buttons
            if (e.target.hasAttribute('data-tab')) {
                const tab = e.target.getAttribute('data-tab');
                this.switchTab(tab);
            }
            
            // Activity filter buttons
            if (e.target.hasAttribute('data-category')) {
                const category = e.target.getAttribute('data-category');
                if (e.target.closest('#activities-tab')) {
                    this.filterActivities(category);
                } else if (e.target.closest('.browse-filters')) {
                    this.filterBrowseActivities(category);
                }
            }
            
            // Add to trip buttons
            if (e.target.hasAttribute('data-activity')) {
                const activityId = e.target.getAttribute('data-activity');
                this.addToTrip(activityId);
            }
            
            // Remove from trip buttons
            if (e.target.hasAttribute('data-remove')) {
                const activityId = e.target.getAttribute('data-remove');
                this.removeFromTrip(activityId);
            }
            
            // Package selection buttons
            if (e.target.hasAttribute('data-package')) {
                const packageId = e.target.getAttribute('data-package');
                this.selectPackage(packageId);
            }
        });
        
        // Handle escape key to close chat and modals
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                if (appState.chatOpen) {
                    this.toggleChat();
                }
                this.closeTripBuilderModal();
            }
        });
        
        // Close modal when clicking outside
        const modal = document.getElementById('trip-builder-modal');
        if (modal) {
            modal.addEventListener('click', (event) => {
                if (event.target === modal) {
                    this.closeTripBuilderModal();
                }
            });
        }
        
        console.log('Event listeners setup complete');
    }
}
// Initialize the application
let app;

document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM loaded, starting initialization...');
    app = new MyHiveApp();
    
    // Make key functions available globally for debugging
    window.MyHiveApp = {
        navigateTo: (path) => app.navigateTo(path),
        handleDestinationClick: (id) => app.handleDestinationClick(id),
        switchTab: (tab) => app.switchTab(tab),
        toggleChat: () => app.toggleChat(),
        addToTrip: (id) => app.addToTrip(id),
        selectPackage: (id) => app.selectPackage(id)
    };
});