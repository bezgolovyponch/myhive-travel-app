import {Helmet} from 'react-helmet-async';
import {Link} from 'react-router-dom';
import {SITE_URL} from '../services/config';
import {COMPANY, POLICY_EFFECTIVE_DATE} from '../legal/companyInfo';
import './PolicyPage.css';

// Static Refund & Cancellation Policy — tiered by days before the activity date.
// The day windows and percentages are business decisions; edit the CANCELLATION
// list below to change them. Have a qualified lawyer confirm it against Czech/EU
// consumer law before relying on it.
function RefundPolicyPage() {
    return (
        <div className="policy-page">
            <Helmet>
                <title>Refund &amp; Cancellation Policy — Trivlu</title>
                <meta name="description"
                      content="Trivlu's cancellation and refund terms for tours, activities, and packages."/>
                <link rel="canonical" href={`${SITE_URL}/refund-policy`}/>
            </Helmet>
            <section className="page-hero">
                <h1>Refund &amp; Cancellation Policy</h1>
            </section>
            <section className="policy-section">
                <p className="policy-dates">
                    Effective date: {POLICY_EFFECTIVE_DATE}<br/>
                    Last updated: {POLICY_EFFECTIVE_DATE}
                </p>

                <p>
                    This policy explains how cancellations and refunds work for bookings made through{' '}
                    {COMPANY.tradeName} ({COMPANY.legalName}). It forms part of our{' '}
                    <Link to="/terms">Terms &amp; Conditions</Link>. All time windows below are counted from the
                    scheduled start date of the activity.
                </p>

                <h2>Cancellation by you</h2>
                <p>
                    If you need to cancel a confirmed booking, the refund depends on how far in advance you
                    tell us:
                </p>
                <ul>
                    <li>
                        <strong>30 or more days before the start date</strong> — full refund of amounts paid.
                    </li>
                    <li>
                        <strong>15 to 29 days before the start date</strong> — 50% refund of amounts paid.
                    </li>
                    <li>
                        <strong>14 or fewer days before the start date</strong> — no refund, as we and our local
                        partners have by then committed resources to your booking.
                    </li>
                </ul>
                <p>
                    Non-refundable third-party costs (for example, card processing fees or fees for tickets
                    that a partner cannot cancel) are deducted from any refund. Where a specific activity has
                    stricter supplier terms, those will be shown to you before you book and will take
                    precedence.
                </p>
                <p>
                    <strong>Reducing the size of your group</strong> counts as a cancellation of the places you
                    remove, and the same time windows above apply to those places. If the balance for a booking
                    is not paid by its due date, we may treat the booking as cancelled within 14 days.
                </p>

                <h2>How to cancel</h2>
                <p>
                    To cancel or change a booking, email{' '}
                    <a href={`mailto:${COMPANY.bookingsEmail}`}>{COMPANY.bookingsEmail}</a> (or{' '}
                    <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a>) with your booking
                    reference. We will confirm your cancellation by email. The refund is calculated from the
                    date we receive your request, so the time we take to review and confirm it will never move
                    you into a lower refund tier.
                </p>

                <h2>Changes and rescheduling</h2>
                <p>
                    We will always try to accommodate a change to your booking (such as a different date or
                    group size), subject to availability and any difference in price. Requests made 15 or more
                    days before the start date are handled free of charge where possible; closer to the date,
                    the cancellation terms above may apply.
                </p>

                <h2>Cancellation or changes by a partner</h2>
                <p>
                    Very occasionally a local partner may need to change or cancel a confirmed booking. If a
                    partner cancels, you may choose a suitable alternative we arrange or a full refund of
                    amounts paid for the affected services. If a significant change is necessary, we will let
                    you know as soon as possible and offer the same choice.
                </p>

                <h2>Events beyond our control (force majeure)</h2>
                <p>
                    If an activity cannot go ahead because of circumstances beyond our reasonable control (such
                    as extreme weather, strikes, or government restrictions), we will offer you a rescheduled
                    date or a credit where possible. Refunds in these cases are limited to amounts we are able
                    to recover from the relevant suppliers.
                </p>

                <h2>No-shows and late arrival</h2>
                <p>
                    If you do not arrive for a booked activity, or arrive too late to take part, the booking is
                    treated as a cancellation within 14 days and no refund is due.
                </p>

                <h2>Refusal or removal during an activity</h2>
                <p>
                    As set out in our <Link to="/terms">Terms &amp; Conditions</Link>, a local partner may
                    refuse or remove a participant on reasonable safety grounds — for example if they are unfit
                    to take part, intoxicated where the activity requires sobriety, or breaking the law or venue
                    rules. No refund is due for a participant refused or removed on these grounds.
                </p>

                <h2>How refunds are paid</h2>
                <p>
                    Approved refunds are made to your original payment method through our payment provider,
                    Stripe, normally within 5–10 business days of approval. The time for the funds to appear
                    depends on your bank or card issuer.
                </p>

                <h2>Your statutory rights</h2>
                <p>
                    This policy does not affect any mandatory rights you have as a consumer under Czech and EU
                    law. Note that the statutory 14-day right of withdrawal for online purchases generally does
                    not apply to leisure activities booked for a specific date or period, as permitted by law.
                </p>

                <h2>Contact us</h2>
                <p>
                    For anything about cancellations or refunds, email{' '}
                    <a href={`mailto:${COMPANY.bookingsEmail}`}>{COMPANY.bookingsEmail}</a> or write to us at{' '}
                    {COMPANY.address}.
                </p>
            </section>
        </div>
    );
}

export default RefundPolicyPage;
