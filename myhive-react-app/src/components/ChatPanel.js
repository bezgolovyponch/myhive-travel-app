import {useContext, useEffect, useRef, useState} from 'react';
import {AppContext} from '../context/AppContext';

function ChatPanel() {
  const { state, dispatch } = useContext(AppContext);
  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [state.chatMessages]);

  useEffect(() => {
    if (!state.chatOpen && !state.autoEngaged) {
      const timeout = setTimeout(() => {
        dispatch({
          type: 'ADD_CHAT_MESSAGE',
          message: {
            sender: 'ai',
            text: 'Looking for an epic weekend? Tenerife offers amazing volcanic adventures and beach parties year-round!'
          }
        });
        dispatch({type: 'SET_AUTO_ENGAGED', value: true});
      }, 3000);
      return () => clearTimeout(timeout);
    }
  }, [state.chatOpen, state.autoEngaged, dispatch]);

  const handleSendMessage = () => {
    if (inputValue.trim()) {
      // Add user message
      dispatch({ 
        type: 'ADD_CHAT_MESSAGE', 
        message: { sender: 'user', text: inputValue } 
      });
      
      // Simulate AI response
      setTimeout(() => {
        dispatch({
          type: 'ADD_CHAT_MESSAGE',
          message: { 
            sender: 'ai', 
            text: getAIResponse(inputValue, state.currentTab) 
          }
        });
      }, 1000);
      
      setInputValue('');
    }
  };

  const getAIResponse = (input, tab) => {
    const userMessageLower = input.toLowerCase();
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
      if (tab === 'activities') {
        aiResponse = "Based on what's popular, I'd recommend starting with the Sunset Boat Party - it's a great way to meet people and see the island from the water!";
      } else if (tab === 'packages') {
        aiResponse = "The Beach Bliss Package is very popular - it has a perfect mix of party and relaxation. You can always customize it in the Trip Builder!";
      } else if (tab === 'trip-builder') {
        aiResponse = `You have ${state.tripItems.length} activities selected. ${state.tripItems.length < 2 ? 'Consider adding more for a full experience!' : 'This looks like a great balanced trip!'}`;
      }
    } else if (userMessageLower.includes('price') || userMessageLower.includes('cost') || userMessageLower.includes('budget')) {
      aiResponse = "Great value options include the Epic Pub Crawl (€35 pp) and VIP Beach Club Access (€45 pp). Our packages offer even better deals when you book multiple activities together!";
    } else if (userMessageLower.includes('group') || userMessageLower.includes('friends')) {
      aiResponse = "Perfect for groups! The Sunset Boat Party and Epic Pub Crawl are especially great for meeting other travelers. All our activities are group-friendly!";
    } else if (userMessageLower.includes('hi') || userMessageLower.includes('hello') || userMessageLower.includes('help')) {
      aiResponse = "Hello! I'm here to help you create the perfect Tenerife experience. Are you looking for adventure, nightlife, relaxation, or a mix of everything?";
    }

    return aiResponse;
  };

  return (
      <>
        <div className={`chat-panel ${state.chatOpen ? 'open' : ''}`}>
          <div className="chat-header">
            <h3 className="chat-title">AI Travel Assistant</h3>
            <button
                className="chat-close-btn"
                onClick={() => dispatch({type: 'TOGGLE_CHAT'})}
            >
              ×
            </button>
          </div>
          <div className="chat-messages">
            {state.chatMessages.map((msg, i) => (
                <div key={i} className={`chat-message ${msg.sender}`}>
                  <div className="chat-avatar">{msg.sender === 'ai' ? 'AI' : 'You'}</div>
                  <div className="chat-bubble">{msg.text}</div>
                </div>
            ))}
            <div ref={messagesEndRef}/>
          </div>
          <div className="chat-input-container">
            <input
                type="text"
                className="chat-input"
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
                placeholder="Tell me about your ideal trip..."
            />
          </div>
        </div>
        <button className="chat-trigger-btn" onClick={() => dispatch({type: 'TOGGLE_CHAT'})}>🤖</button>
      </>
  );
}

export default ChatPanel;
