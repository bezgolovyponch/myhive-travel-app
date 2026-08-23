import {useEffect, useReducer, useRef, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import {useT} from '../i18n';
import './ChatPanel.css';

const makeInitialChatState = (t) => ({
  chatOpen: false,
  chatMessages: [
    {sender: 'ai', text: t('greeting')}
  ],
  autoEngaged: false,
});

function chatReducer(state, action) {
  switch (action.type) {
    case 'TOGGLE_CHAT':
      return {...state, chatOpen: !state.chatOpen};
    case 'SET_AUTO_ENGAGED':
      return {...state, autoEngaged: action.value};
    case 'ADD_CHAT_MESSAGE':
      return {...state, chatMessages: [...state.chatMessages, action.message]};
    default:
      return state;
  }
}

function ChatPanel() {
  const t = useT('chat');
  const [state, dispatch] = useReducer(chatReducer, t, makeInitialChatState);
  const {state: trip} = useTrip();
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
            text: t('autoEngage')
          }
        });
        dispatch({type: 'SET_AUTO_ENGAGED', value: true});
      }, 3000);
      return () => clearTimeout(timeout);
    }
  }, [state.chatOpen, state.autoEngaged, dispatch, t]);

  const handleSendMessage = () => {
    if (inputValue.trim()) {
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
    let aiResponse = t('responses.default');

    if (userMessageLower.includes('party') || userMessageLower.includes('nightlife') || userMessageLower.includes('club')) {
      aiResponse = t('responses.nightlife');
    } else if (userMessageLower.includes('adventure') || userMessageLower.includes('active')) {
      aiResponse = t('responses.adventure');
    } else if (userMessageLower.includes('beach') || userMessageLower.includes('relax') || userMessageLower.includes('spa')) {
      aiResponse = t('responses.relax');
    } else if (userMessageLower.includes('package') || userMessageLower.includes('deal')) {
      aiResponse = t('responses.packages');
    } else if (userMessageLower.includes('suggest') || userMessageLower.includes('recommend')) {
      if (tab === 'packages') {
        aiResponse = t('responses.suggestPackages');
      } else if (tab === 'trip-builder') {
        aiResponse = `${t('responses.suggestTripBuilder', {count: trip.tripItems.length})} ${trip.tripItems.length < 2 ? t('responses.suggestAddMore') : t('responses.suggestBalanced')}`;
      } else {
        aiResponse = t('responses.suggestDefault');
      }
    } else if (userMessageLower.includes('price') || userMessageLower.includes('cost') || userMessageLower.includes('budget')) {
      aiResponse = t('responses.prices');
    } else if (userMessageLower.includes('group') || userMessageLower.includes('friends')) {
      aiResponse = t('responses.groups');
    } else if (userMessageLower.includes('hi') || userMessageLower.includes('hello') || userMessageLower.includes('help')) {
      aiResponse = t('responses.hello');
    }

    return aiResponse;
  };

  return (
      <>
        <div className={`chat-panel ${state.chatOpen ? 'open' : ''}`}>
          <div className="chat-header">
            <h3 className="chat-title">{t('title')}</h3>
            <button
                type="button"
                className="chat-close-btn"
                aria-label={t('closeAria')}
                onClick={() => dispatch({type: 'TOGGLE_CHAT'})}
            >
              ×
            </button>
          </div>
          <div className="chat-messages">
            {state.chatMessages.map((msg, i) => (
                <div key={i} className={`chat-message ${msg.sender}`}>
                  <div className="chat-avatar">{msg.sender === 'ai' ? t('avatarAi') : t('avatarYou')}</div>
                  <div className="chat-bubble">{msg.text}</div>
                </div>
            ))}
            <div ref={messagesEndRef}/>
          </div>
          <div className="chat-input-container">
            <input
                type="text"
                className="chat-input"
                aria-label={t('inputAria')}
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
                placeholder={t('inputPlaceholder')}
            />
            <button
                type="button"
                className="chat-send-btn"
                aria-label={t('sendAria')}
                onClick={handleSendMessage}
                disabled={!inputValue.trim()}
            >
              {t('send')}
            </button>
          </div>
        </div>
        <button
            type="button"
            className="chat-trigger-btn"
            aria-label={t('openAria')}
            onClick={() => dispatch({type: 'TOGGLE_CHAT'})}
        >🤖</button>
      </>
  );
}

export default ChatPanel;
