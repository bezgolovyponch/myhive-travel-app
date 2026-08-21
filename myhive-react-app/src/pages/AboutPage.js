import PageHead from '../components/PageHead';
import {SITE_URL} from '../services/config';
import {useT} from '../i18n';
import './AboutPage.css';

function AboutPage() {
    const t = useT('about');
    return (
        <div className="about-page">
            <PageHead>
                <title>About Trivlu — Group Travel Made Easy</title>
                <meta name="description"
                      content="We built Trivlu so best men can plan a stag the whole group actually agrees on. Here's who we are and why."/>
                <link rel="canonical" href={`${SITE_URL}/about`}/>
            </PageHead>
            <section className="page-hero">
                <h1>{t('hero.title')}</h1>
                <p>{t('hero.subtitle')}</p>
            </section>

            <section className="about-section">
                <h2>{t('mission.title')}</h2>
                <p>{t('mission.p1')}</p>
                <p>{t('mission.p2')}</p>
            </section>

            <section className="about-section about-values">
                <h2>{t('values.title')}</h2>
                <div className="values-grid">
                    <div className="value-card">
                        <h3>{t('values.adventure.title')}</h3>
                        <p>{t('values.adventure.body')}</p>
                    </div>
                    <div className="value-card">
                        <h3>{t('values.stress.title')}</h3>
                        <p>{t('values.stress.body')}</p>
                    </div>
                    <div className="value-card">
                        <h3>{t('values.together.title')}</h3>
                        <p>{t('values.together.body')}</p>
                    </div>
                </div>
            </section>

            <section className="about-section">
                <h2>{t('story.title')}</h2>
                <p>{t('story.p1')}</p>
                <p>{t('story.p2')}</p>
            </section>
        </div>
    );
}

export default AboutPage;
