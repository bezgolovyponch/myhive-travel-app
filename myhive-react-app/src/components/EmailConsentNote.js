import {useT} from '../i18n';

// GDPR notice shown wherever we capture an email that later feeds reminder emails.
function EmailConsentNote() {
    const t = useT('contact');
    return (
        <p style={{fontSize: '0.8rem', color: '#6c757d', margin: '4px 0 0'}}>
            {t('consentNote')}
        </p>
    );
}

export default EmailConsentNote;
