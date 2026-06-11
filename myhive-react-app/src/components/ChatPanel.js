import {useContext, useEffect, useRef, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import './ChatPanel.css';

function ChatPanel() {
  const { state, dispatch } = useContext(AppContext);
  const location = useLocation();
  const currentTab = new URLSearchParams(location.search).get('tab') || 'activities';
  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef(null);
  const replyTimeoutsRef = useRef(new Set());

  // Clear pending canned replies if the panel unmounts mid-conversation.
  useEffect(() => {
    const timeouts = replyTimeoutsRef.current;
    return () => timeouts.forEach(clearTimeout);
  }, []);

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
            text: 'Planning a stag do? Tell me what your group is into and I\'ll point you to the right activities!'
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
      
      // Simulate AI response. Each message gets its own timeout — sending a
      // second message quickly must not cancel the first reply.
      const id = setTimeout(() => {
        replyTimeoutsRef.current.delete(id);
        dispatch({
          type: 'ADD_CHAT_MESSAGE',
          message: {
            sender: 'ai',
            text: getAIResponse(inputValue, currentTab)
          }
        });
      }, 1000);
      replyTimeoutsRef.current.add(id);
      
      setInputValue('');
    }
  };

  // Canned responses are destination-agnostic on purpose — the catalog
  // changes per destination, so naming specific activities here goes stale.
  const getAIResponse = (input, tab) => {
    const userMessageLower = input.toLowerCase();
    let aiResponse = "I can help you plan the perfect group getaway! What interests you most?";

    if (userMessageLower.includes('party') || userMessageLower.includes('nightlife') || userMessageLower.includes('club')) {
      aiResponse = "For epic nightlife, check the Nightlife category in Activities — bar crawls, club entries and boat parties are group favourites.";
    } else if (userMessageLower.includes('adventure') || userMessageLower.includes('active')) {
      aiResponse = "Adventure awaits! Browse the Adventure category for go-karting, water sports and adrenaline activities your group can do together.";
    } else if (userMessageLower.includes('beach') || userMessageLower.includes('relax') || userMessageLower.includes('spa')) {
      aiResponse = "Perfect for unwinding — look for spa, beach club and relaxed daytime activities to balance out the big nights.";
    } else if (userMessageLower.includes('package') || userMessageLower.includes('deal')) {
      aiResponse = "Our packages bundle several activities at a discount — check the Packages tab on the destination page and customize any of them in the Trip Builder!";
    } else if (userMessageLower.includes('suggest') || userMessageLower.includes('recommend')) {
      if (tab === 'packages') {
        aiResponse = "Packages are the easiest start — a ready-made mix of activities at a discount that you can still customize in the Trip Builder!";
      } else if (tab === 'trip-builder') {
        aiResponse = `You have ${state.tripItems.length} activities selected. ${state.tripItems.length < 2 ? 'Consider adding more for a full experience!' : 'This looks like a great balanced trip!'}`;
      } else {
        aiResponse = "Start with the featured activities on the home page — they're the most popular with groups. Add anything you like to the Trip Builder!";
      }
    } else if (userMessageLower.includes('price') || userMessageLower.includes('cost') || userMessageLower.includes('budget')) {
      aiResponse = "Prices are shown per person on every activity card, and packages give you a discount for booking several activities together. The Trip Builder totals everything for your group size.";
    } else if (userMessageLower.includes('group') || userMessageLower.includes('friends')) {
      aiResponse = "Everything here is built for groups! You can even start a group vote from the Trip Builder so the whole crew picks the activities together.";
    } else if (userMessageLower.includes('hi') || userMessageLower.includes('hello') || userMessageLower.includes('help')) {
      aiResponse = "Hello! I'm here to help you plan the perfect trip. Are you looking for adventure, nightlife, relaxation, or a mix of everything?";
    }

    return aiResponse;
  };

  return (
      <>
        <div className={`chat-panel ${state.chatOpen ? 'open' : ''}`}>
          <div className="chat-header">
            <h3 className="chat-title">AI Travel Assistant</h3>
            <button
                type="button"
                className="chat-close-btn"
                aria-label="Close chat"
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
                aria-label="Chat message"
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
                placeholder="Tell me about your ideal trip..."
            />
            <button
                type="button"
                className="chat-send-btn"
                aria-label="Send message"
                onClick={handleSendMessage}
                disabled={!inputValue.trim()}
            >
              Send
            </button>
          </div>
        </div>
        <button
            type="button"
            className="chat-trigger-btn"
            aria-label="Open chat assistant"
            onClick={() => dispatch({type: 'TOGGLE_CHAT'})}
        >🤖</button>
      </>
  );
}

export default ChatPanel;
