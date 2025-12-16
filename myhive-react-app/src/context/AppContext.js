import { createContext, useReducer } from 'react';

// Initial data mirroring original appData
const initialDestinations = [
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
  // Other destinations...
];

const initialActivities = [
  {
    id: "sunset-boat-party",
    title: "Sunset Boat Party",
    category: "nightlife",
    description: "3-hour catamaran cruise with live DJ and open bar",
    price: "€69 pp",
    image: "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=400&h=300&fit=crop"
  },
  // Other activities...
];

const initialPackages = [
  {
    id: "action-stag-weekend",
    title: "Action Stag Weekend",
    description: "2 days of adventure and nightlife with pub crawl, jet ski, and VIP club entry",
    activities: ["pub-crawl", "jet-ski-adventure", "sunset-boat-party"],
    price: "€189 pp",
    image: "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=400&h=300&fit=crop"
  },
  // Other packages...
];

export const AppContext = createContext();

export function AppProvider({ children }) {
  const initialState = {
    destinations: initialDestinations,
    activities: initialActivities,
    packages: initialPackages,
    currentPath: '/',
    currentTab: 'activities',
    tripItems: [],
    chatOpen: false,
    chatMessages: [
      { sender: 'ai', text: 'Hi! I\'m your AI travel assistant. What type of trip are you looking for?' }
    ]
  };

  const reducer = (state, action) => {
    switch (action.type) {
      case 'NAVIGATE':
        return { ...state, currentPath: action.path };
      case 'ADD_TO_TRIP':
        if (!state.tripItems.some(item => item.id === action.activity.id)) {
          return { 
            ...state, 
            tripItems: [...state.tripItems, action.activity] 
          };
        }
        return state;
      case 'REMOVE_FROM_TRIP':
        return { 
          ...state, 
          tripItems: state.tripItems.filter(item => item.id !== action.activityId)
        };
      case 'SELECT_PACKAGE':
        // Add all package activities to trip
        const newItems = [...state.tripItems];
        action.pkg.activities.forEach(actId => {
          const activity = state.activities.find(a => a.id === actId);
          if (activity && !newItems.some(item => item.id === actId)) {
            newItems.push(activity);
          }
        });
        return { ...state, tripItems: newItems };
      case 'SWITCH_TAB':
        return { ...state, currentTab: action.tab };
      case 'TOGGLE_CHAT':
        return { ...state, chatOpen: !state.chatOpen };
      case 'ADD_CHAT_MESSAGE':
        return { 
          ...state, 
          chatMessages: [...state.chatMessages, action.message] 
        };
      default:
        return state;
    }
  };

  const [state, dispatch] = useReducer(reducer, initialState);

  return (
    <AppContext.Provider value={{ state, dispatch }}>
      {children}
    </AppContext.Provider>
  );
}
