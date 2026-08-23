import PageHead from '../components/PageHead';
import {SITE_URL} from '../services/config';
import {useT} from '../i18n';
import './AboutPage.css';

function AboutPage() {
    const t = useT('about');
    const tMeta = useT('meta');
    return (
        <div className="about-page">
            <PageHead>
                <title>{tMeta('about.title')}</title>
                <meta name="description" content={tMeta('about.description')}/>
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
