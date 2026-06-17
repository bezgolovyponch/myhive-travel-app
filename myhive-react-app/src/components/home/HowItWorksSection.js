import {pushEvent} from '../../utils/analytics';
import './HowItWorksSection.css';

const STEPS = [
    {icon: '🎯', title: 'Define your stag style', text: 'wild or classy, chill or adrenaline'},
    {icon: '👆', title: 'Handpick the shortlist', text: 'pick what the group gets to vote on'},
    {icon: '🗳️', title: 'Send the vote link', text: 'your mates pick their favourites'},
    {icon: '✏️', title: 'Review & confirm', text: 'add, remove or tweak activities before you book'},
];

function HowItWorksSection({onStartVote}) {
    return (
        <section className="how-it-works">
            <h2 className="section-title">The Smartest Way to Plan a Stag Do</h2>
            <p className="section-subtitle">
                Our Trip Builder uses group voting to turn everyone's preferences into one perfect stag do package.
            </p>
            <div className="how-it-works-steps">
                {STEPS.map((step, index) => (
                    <div key={step.title} className="how-it-works-step">
                        <span className="step-number">{index + 1}</span>
                        <span className="step-icon" aria-hidden="true">{step.icon}</span>
                        <h3 className="step-title">{step.title}</h3>
                        <p className="step-text">{step.text}</p>
                    </div>
                ))}
            </div>
            <button
                className="btn btn--primary btn--lg"
                onClick={() => {
                    pushEvent('cta_click', {cta_label: 'Start Group Vote', block: 'trip_builder'});
                    onStartVote();
                }}
            >
                Start Group Vote
            </button>
        </section>
    );
}

export default HowItWorksSection;
