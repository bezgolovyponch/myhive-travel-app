import {Link} from 'react-router-dom';
import './PaymentReturnPages.css';

function PaymentCancelledPage() {
    return (
        <div className="payment-return">
            <h1>Payment cancelled</h1>
            <p>No charge was made. You can return to your trip and try again whenever you're ready.</p>
            <Link to="/" className="btn btn--primary">Back to home</Link>
        </div>
    );
}

export default PaymentCancelledPage;
