import {pushEvent} from '../../utils/analytics';
import SwipeMomentCard from './SwipeMomentCard';
import VoteMomentCard from './VoteMomentCard';
import stepPickImg from '../../assets/home/step-pick.jpg';
import stepLimoImg from '../../assets/home/step-limo.png';
import './HowItWorksSection.css';

// Layout per the approved how-it-works-v12 mockup: three equal cards — swipe
// deck, live vote, booked photo — each captioned with a numbered step title.
const STEPS = [
    {
        title: 'Pick activities to vote on',
        visual: <SwipeMomentCard image={stepPickImg}/>,
        wrapperClass: 'step-img step-img--component',
    },
    {
        title: 'Share the vote link',
        visual: <VoteMomentCard/>,
        wrapperClass: 'step-img step-img--component',
    },
    {
        title: 'Book the winners',
        visual: (
            <>
                <img src={stepLimoImg} alt="" loading="lazy"/>
                <div className="booked">
                    <i className="ph ph-check-circle"/>
                    <div>
                        <div className="booked-t">Booking confirmed</div>
                        <div className="booked-s">Prague &middot; 19&ndash;21 Jun &middot; 8 lads</div>
                    </div>
                </div>
            </>
        ),
        wrapperClass: 'step-img step-img--photo',
    },
];

function HowItWorksSection({onStartVote}) {
    return (
        <section className="how-it-works">
            <h2 className="section-title">Let the group decide. You just book it.</h2>
            <p className="section-subtitle">
                Everyone votes, you confirm, we handle the rest.
            </p>
            <div className="how-it-works-steps">
                {STEPS.map((step, index) => (
                    <div key={step.title} className="how-it-works-step">
                        <div className={step.wrapperClass}>
                            {step.visual}
                        </div>
                        <div className="step-body">
                            <div className="step-head">
                                <span className="step-number">{index + 1}</span>
                                <h3 className="step-title">{step.title}</h3>
                            </div>
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
