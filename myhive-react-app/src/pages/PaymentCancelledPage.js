import {Link} from 'react-router-dom';
import {useT} from '../i18n';
import './PaymentReturnPages.css';

function PaymentCancelledPage() {
    const t = useT('payment');
    return (
        <div className="payment-return">
            <h1>{t('cancelled.title')}</h1>
            <p>{t('cancelled.body')}</p>
            <Link to="/" className="btn btn--primary">{t('backToHome')}</Link>
        </div>
    );
}

export default PaymentCancelledPage;
