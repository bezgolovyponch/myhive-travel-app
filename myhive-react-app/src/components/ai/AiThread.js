import {
    AssistantRuntimeProvider,
    ComposerPrimitive,
    MessagePrimitive,
    ThreadPrimitive,
    useExternalStoreRuntime,
} from '@assistant-ui/react';
import {useT} from '../../i18n';
import './AiThread.css';

// assistant-ui primitives over our own planner state (useAiPlanner): the
// runtime only renders — the transcript, the pending flag and the chips are
// ours, which is what ExternalStoreRuntime is for. Unstyled primitives, so the
// look stays in AiThread.css with the rest of the site's hand-written CSS.

const convertMessage = (m) => ({
    id: m.id,
    role: m.role,
    content: m.content,
    createdAt: m.at ? new Date(m.at) : undefined,
});

const textOf = (appendMessage) => appendMessage.content
    .filter((part) => part.type === 'text')
    .map((part) => part.text)
    .join('\n');

// Codes with their own line in aiPlanner.errors; anything else reads as generic.
const KNOWN_ERRORS = new Set([
    'LLM_UNAVAILABLE', 'LLM_TIMEOUT', 'SESSION_BUSY', 'AI_BUSY', 'SESSION_NOT_FOUND',
    'SESSION_TURN_LIMIT', 'GENERATION_LIMIT', 'SESSION_DAILY_LIMIT', 'AI_DISABLED', 'NETWORK', 'TURNSTILE_FAILED',
]);

const Text = ({text}) => <p className="ai-thread-text">{text}</p>;

// While a turn is in flight the runtime shows an empty assistant message;
// this is what it renders.
function Typing() {
    const t = useT('aiPlanner');
    return (
        <span className="ai-typing" role="status" aria-live="polite">
            <span/><span/><span/>
            <span className="visually-hidden">{t('thinking')}</span>
        </span>
    );
}

const PARTS = {Text, Empty: Typing};

function UserMessage() {
    return (
        <MessagePrimitive.Root className="ai-msg ai-msg-user">
            <div className="ai-bubble"><MessagePrimitive.Parts components={PARTS}/></div>
        </MessagePrimitive.Root>
    );
}

function AssistantMessage() {
    return (
        <MessagePrimitive.Root className="ai-msg ai-msg-assistant">
            <span className="ai-avatar" aria-hidden="true"><i className="ph ph-sparkle"/></span>
            <div className="ai-bubble"><MessagePrimitive.Parts components={PARTS}/></div>
        </MessagePrimitive.Root>
    );
}

const MESSAGE_COMPONENTS = {UserMessage, AssistantMessage};

/**
 * @param planner  the useAiPlanner() result
 * @param variant  'full' (first turn, the whole screen) | 'drawer' (the dock)
 * @param emptyState  rendered while the transcript is empty (the intro + starters)
 * @param afterMessages  rendered under the latest reply (the trim cards)
 */
function AiThread({planner, variant = 'full', placeholder, emptyState = null, afterMessages = null, footer = null}) {
    const t = useT('aiPlanner');
    const {messages, sending, building, suggestedReplies, error, send, retry} = planner;

    const runtime = useExternalStoreRuntime({
        messages,
        convertMessage,
        isRunning: sending,
        // While packages are being built every message is refused
        // (GENERATION_IN_PROGRESS), so the composer waits too.
        isSendDisabled: building,
        suggestions: sending ? [] : suggestedReplies.map((prompt) => ({prompt})),
        onNew: async (message) => send(textOf(message)),
    });

    return (
        <AssistantRuntimeProvider runtime={runtime}>
            <ThreadPrimitive.Root className={`ai-thread ai-thread-${variant}`}>
                <ThreadPrimitive.Viewport className="ai-thread-viewport" autoScroll>
                    {messages.length === 0 && emptyState}
                    <ThreadPrimitive.Messages components={MESSAGE_COMPONENTS}/>
                    {!sending && afterMessages}

                    {building && !sending && (
                        <div className="ai-status" role="status" aria-live="polite">
                            <span className="ai-status-dot" aria-hidden="true"/> {t('building')}
                        </div>
                    )}
                    {error && (
                        <div className="ai-error" role="alert">
                            <span>{t(`errors.${KNOWN_ERRORS.has(error.code) ? error.code : 'generic'}`)}</span>
                            {error.retryText && (
                                <button type="button" className="ai-error-retry" onClick={retry}>
                                    {t('retry')}
                                </button>
                            )}
                        </div>
                    )}
                </ThreadPrimitive.Viewport>

                <div className="ai-thread-bottom">
                    <div className="ai-suggestions">
                        <ThreadPrimitive.Suggestions>
                            {({suggestion}) => (
                                <ThreadPrimitive.Suggestion
                                    className="ai-chip"
                                    prompt={suggestion.prompt}
                                    send
                                >
                                    {suggestion.prompt}
                                </ThreadPrimitive.Suggestion>
                            )}
                        </ThreadPrimitive.Suggestions>
                    </div>
                    <ComposerPrimitive.Root className="ai-composer">
                        <ComposerPrimitive.Input
                            className="ai-composer-input"
                            placeholder={placeholder || t('placeholder')}
                            aria-label={t('inputAria')}
                            maxLength={1000}
                            maxRows={5}
                            submitOnEnter
                            autoFocus={variant === 'full'}
                        />
                        <ComposerPrimitive.Send className="ai-composer-send" aria-label={t('sendAria')}>
                            <i className="ph ph-paper-plane-right" aria-hidden="true"/>
                        </ComposerPrimitive.Send>
                    </ComposerPrimitive.Root>
                    {footer}
                </div>
            </ThreadPrimitive.Root>
        </AssistantRuntimeProvider>
    );
}

export default AiThread;
