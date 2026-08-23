import {pushEvent} from '../../utils/analytics';
import {assetUrl} from '../../utils/assetUrl';
import SwipeMomentCard from './SwipeMomentCard';
import VoteMomentCard from './VoteMomentCard';
import stepPickImg from '../../assets/home/step-pick.jpg';
import stepLimoImg from '../../assets/home/step-limo.png';
import {useT} from '../../i18n';
import './HowItWorksSection.css';

// Own component (not inline JSX in STEPS) so the booked caption can use useT.
function BookedConfirmation() {
    const t = useT('home');
    return (
        <div className="booked">
            <i className="ph ph-check-circle"/>
            <div>
                <div className="booked-t">{t('howItWorks.bookedTitle')}</div>
                <div className="booked-s">{t('howItWorks.bookedSub')}</div>
            </div>
        </div>
    );
}

// Layout per the approved how-it-works-v12 mockup: three equal cards — swipe
// deck, live vote, booked photo — each captioned with a numbered step title.
const STEPS = [
    {
        titleKey: 'howItWorks.step1Title',
        visual: <SwipeMomentCard image={assetUrl(stepPickImg)}/>,
        wrapperClass: 'step-img step-img--component',
    },
    {
        titleKey: 'howItWorks.step2Title',
        visual: <VoteMomentCard/>,
        wrapperClass: 'step-img step-img--component',
    },
    {
        titleKey: 'howItWorks.step3Title',
        visual: (
            <>
                <img src={assetUrl(stepLimoImg)} alt="" loading="lazy"/>
                <BookedConfirmation/>
            </>
        ),
        wrapperClass: 'step-img step-img--photo',
    },
];

function HowItWorksSection({onStartVote}) {
    const t = useT('home');
    return (
        <section className="how-it-works">
            <h2 className="section-title">{t('howItWorks.heading')}</h2>
            <p className="section-subtitle">
                {t('howItWorks.subtitle')}
            </p>
            <div className="how-it-works-steps">
                {STEPS.map((step, index) => (
                    <div key={step.titleKey} className="how-it-works-step">
                        <div className={step.wrapperClass}>
                            {step.visual}
                        </div>
                        <div className="step-body">
                            <div className="step-head">
                                <span className="step-number">{index + 1}</span>
                                <h3 className="step-title">{t(step.titleKey)}</h3>
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
                <i className="ph ph-check-square" aria-hidden="true"/> {t('howItWorks.startVoteCta')}
            </button>
        </section>
    );
}

export default HowItWorksSection;
