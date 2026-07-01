import {pushEvent} from '../../utils/analytics';
import './HowItWorksSection.css';

const STEPS = [
    {
        title: 'Define your stag style',
        text: 'Wild or classy, chill or adrenaline',
        img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.38.35.png',
        objectPosition: 'top',
    },
    {
        title: 'Handpick the shortlist',
        text: 'Pick what the group gets to vote on',
        img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.52.51.jpg',
        objectPosition: 'center',
    },
    {
        title: 'Send the vote link',
        text: 'Your mates pick their favourites',
        img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.55.15.png',
        objectPosition: 'center',
    },
    {
        title: 'Review & confirm',
        text: 'Add, remove or tweak before you book',
        img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.40.55.jpg',
        objectPosition: 'left top',
    },
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
                        <div className="step-img">
                            <img src={step.img} alt="" loading="lazy" style={{objectPosition: step.objectPosition}}/>
                        </div>
                        <div className="step-body">
                            <div className="step-head">
                                <span className="step-number">{index + 1}</span>
                                <h3 className="step-title">{step.title}</h3>
                            </div>
                            <p className="step-text">{step.text}</p>
                        </div>
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
                <i className="ph ph-check-square" aria-hidden="true"/> Start Group Vote
            </button>
        </section>
    );
}

export default HowItWorksSection;
