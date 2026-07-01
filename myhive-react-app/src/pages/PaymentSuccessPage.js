import {Link, useSearchParams} from 'react-router-dom';
import './PaymentReturnPages.css';

function PaymentSuccessPage() {
    const [params] = useSearchParams();
    const booking = params.get('booking');

    return (
        <div className="payment-return">
            <h1>Thank you — your payment is on its way!</h1>
            <p>We're confirming your payment with our provider. You'll get an email once it's processed.</p>
            {booking && <p className="payment-return__ref">Booking reference: {booking}</p>}
            <Link to="/" className="btn btn--primary">Back to home</Link>
        </div>
    );
}

export default PaymentSuccessPage;
