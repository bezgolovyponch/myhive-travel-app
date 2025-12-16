import { useContext, useState, useEffect, useRef } from 'react';
import { AppContext } from '../context/AppContext';

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
    // Response logic based on user input and current tab
    const responses = {
      activities: 'I can help recommend activities based on your interests!',
      packages: 'Our packages offer great value for group trips.',
      default: 'I can help plan your perfect getaway!'
    };
    return responses[tab] || responses.default;
  };

  return (
    <div className={`chat-panel ${state.chatOpen ? 'open' : ''}`}>
      <div className="chat-header">
        <h3 className="chat-title">AI Travel Assistant</h3>
        <button 
          className="chat-close-btn" 
          onClick={() => dispatch({ type: 'TOGGLE_CHAT' })}
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
        <div ref={messagesEndRef} />
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
  );
}

export default ChatPanel;
