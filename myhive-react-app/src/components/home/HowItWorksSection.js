import {pushEvent} from '../../utils/analytics';
import VoteDemoCard from './VoteDemoCard';
import TinderMomentCard from './TinderMomentCard';
import stepStyleImg from '../../assets/home/step-style.png';
// Placeholder until the "Steak & Tits" catalog photo is supplied — the old
// step-2 swipe screenshot doubles as the tinder-moment photo for now.
import stepShortlistImg from '../../assets/home/step-shortlist.jpg';
// Placeholder until the limousine catalog photo is supplied.
import stepReviewImg from '../../assets/home/step-review.jpg';
import './HowItWorksSection.css';

const STEPS = [
    {
        title: 'Define your stag style',
        text: 'Wild or classy, chill or adrenaline',
        img: stepStyleImg,
        objectPosition: 'top',
    },
    {
        title: 'Handpick the shortlist',
        text: 'Pick what the group gets to vote on',
        visual: <TinderMomentCard image={stepShortlistImg}/>,
    },
    {
        title: 'Send the vote link',
        text: 'Your mates pick their favourites',
        visual: <VoteDemoCard/>,
    },
    {
        title: 'Review & confirm',
        text: 'Add, remove or tweak before you book',
        img: stepReviewImg,
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
                            {step.visual
                                ? step.visual
                                : <img src={step.img} alt="" loading="lazy"
                                       style={{objectPosition: step.objectPosition}}/>}
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
