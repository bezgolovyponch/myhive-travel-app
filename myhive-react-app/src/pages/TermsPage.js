import {Helmet} from 'react-helmet-async';
import {Link} from 'react-router-dom';
import {SITE_URL} from '../services/config';
import {COMPANY, POLICY_EFFECTIVE_DATE} from '../legal/companyInfo';
import './PolicyPage.css';

// Static Terms & Conditions. Good-faith draft written for Trivlu acting as a
// booking agent that arranges each activity/accommodation with an independent
// local partner (the partner, not Trivlu, is the supplier of the activity),
// covering the group-trip specifics a stag/hen operator needs (Group Leader,
// conduct/removal, documents, insurance, complaints).
//
// IMPORTANT: the "agent" label only limits liability if the customer's contract
// is genuinely with the local partner. Bundled "packages" (several services at an
// inclusive price) are likely a "package" under the EU Package Travel Directive,
// which treats whoever combines them as the ORGANISER regardless of this label —
// bringing mandatory pre-contract info and insolvency protection. Have a qualified
// lawyer confirm the role fits how bookings actually operate before relying on it.
function TermsPage() {
    return (
        <div className="policy-page">
            <Helmet>
                <title>Terms &amp; Conditions — Trivlu</title>
                <meta name="description"
                      content="The terms and conditions that apply when you book group trips and experiences through Trivlu."/>
                <link rel="canonical" href={`${SITE_URL}/terms`}/>
            </Helmet>
            <section className="page-hero">
                <h1>Terms &amp; Conditions</h1>
            </section>
            <section className="policy-section">
                <p className="policy-dates">
                    Effective date: {POLICY_EFFECTIVE_DATE}<br/>
                    Last updated: {POLICY_EFFECTIVE_DATE}
                </p>

                <p>
                    These Terms &amp; Conditions ("Terms") govern your use of the {COMPANY.tradeName} website
                    and any booking you make through us. By making a booking you agree to these Terms on behalf
                    of everyone in your group. Please read them together with our{' '}
                    <Link to="/refund-policy">Refund &amp; Cancellation Policy</Link> and{' '}
                    <Link to="/privacy-policy">Privacy Policy</Link>, which form part of these Terms.
                </p>

                <h2>1. Who we are</h2>
                <p>
                    {COMPANY.tradeName} is operated by {COMPANY.legalName}, a company registered in the Czech
                    Republic (company ID {COMPANY.companyId}, {COMPANY.registration}), with its registered
                    office at {COMPANY.address}. You can reach us at{' '}
                    <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a>. {COMPANY.tradeName}{' '}
                    acts as a <strong>booking agent</strong>: we arrange your activities and, where booked, your
                    accommodation with independent local partners. Your contract for each activity or stay is
                    with the local partner that provides it; that partner is responsible for delivering it. Our
                    role is to arrange your booking with reasonable care and skill, and we remain responsible
                    for our own booking service. Nothing in these Terms removes rights you have by law.
                </p>

                <h2>2. Definitions</h2>
                <ul>
                    <li>
                        <strong>Group Leader</strong> — the person who makes the booking on behalf of everyone
                        travelling.
                    </li>
                    <li>
                        <strong>You</strong> — the Group Leader and every member of the group.
                    </li>
                    <li>
                        <strong>Group</strong> — everyone covered by the booking, including anyone added or
                        substituted after booking.
                    </li>
                    <li>
                        <strong>Local partner</strong> — the independent supplier that delivers a particular
                        activity or accommodation and is the party you contract with for that service.
                    </li>
                </ul>

                <h2>3. The Group Leader</h2>
                <p>
                    One person books on behalf of the whole group and is our main point of contact. By booking,
                    the Group Leader confirms they are authorised to act for everyone in the group and that
                    every member is at least 18 years old on the date of the trip. The Group Leader is
                    responsible for giving us accurate details for the group, passing on the information we
                    provide (such as times, meeting points, and activity rules), collecting and paying the
                    amounts due, and making sure the group follows these Terms. We may ask for proof of age.
                </p>

                <h2>4. Prices and payment</h2>
                <p>
                    Prices are shown on the website in the currency indicated and include applicable taxes
                    unless stated otherwise. What is included is listed on each activity or package. Unless we
                    say otherwise, the price does <strong>not</strong> include travel to the destination or to
                    activity meeting points, food and drinks, entry fees that are not listed, transfers, tips,
                    or personal expenses.
                </p>
                <p>
                    Payment is made securely online through our payment provider, Stripe. We take a deposit at
                    the time of booking (the amount is shown before you pay); the remaining balance is due by
                    the date shown in your booking confirmation and, in any event, before the activity date. If
                    the balance is not paid by its due date, we may treat the booking as cancelled by you (see
                    our <Link to="/refund-policy">Refund &amp; Cancellation Policy</Link>). We reserve the right
                    to correct obvious pricing errors before your booking is confirmed. Quotes for custom
                    arrangements are valid for 14 days unless stated otherwise.
                </p>

                <h2>5. Changes by you</h2>
                <p>
                    To change a confirmed booking (for example the date, activities, or number of people),
                    the Group Leader should email us. Changes are subject to availability and are re-priced at
                    the prices in force on the date we confirm them, and may incur charges passed on by a local
                    partner. Adding people to the group may increase the deposit due before we can confirm.
                </p>

                <h2>6. Changes or cancellation by a partner</h2>
                <p>
                    Occasionally a local partner may make a minor change to a booking, such as substituting a
                    hotel or activity of an equivalent standard. If a partner has to make a significant change
                    to, or cancel, a confirmed booking, we will let you know as soon as possible and arrange a
                    suitable alternative or a refund of amounts paid for the affected services.
                </p>

                <h2>7. Cancellation by you</h2>
                <p>
                    Cancellations by you are governed by our{' '}
                    <Link to="/refund-policy">Refund &amp; Cancellation Policy</Link>, which sets out the refund
                    you receive depending on how far in advance you cancel.
                </p>

                <h2>8. Conduct, safety and removal from activities</h2>
                <p>
                    You and your group agree to follow all reasonable safety instructions and rules given by us
                    or our local partners, to behave responsibly, and not to put yourselves or others at risk.
                    A local partner may refuse or remove any participant on reasonable grounds — including being
                    unfit to take part, being intoxicated where the activity requires sobriety, behaving
                    violently or offensively, or breaking the law or the venue's rules. No refund is due for any
                    participant who is refused or removed on these grounds. If the group is more than 30 minutes
                    late for a scheduled activity, the partner may treat that activity as missed, with no
                    refund. Some activities have minimum age, health, or fitness requirements, which are shown
                    before booking.
                </p>

                <h2>9. Damage</h2>
                <p>
                    You are responsible for any loss or damage you cause to the property of a local partner or
                    a third party, or to any person, and you agree to settle such costs directly with the party
                    concerned.
                </p>

                <h2>10. Travel documents, passports and visas</h2>
                <p>
                    You are responsible for holding valid passports or identity documents, any visas, and
                    meeting the entry requirements of the destination. We are not responsible if a member of
                    the group is unable to travel or take part because they do not have the required documents.
                </p>

                <h2>11. Health, fitness and medical conditions</h2>
                <p>
                    Please tell us before booking of any medical condition or disability that may affect
                    participation in an activity. If we reasonably consider an activity unsuitable or unsafe for
                    a member and cannot offer a safe alternative, that member's booking for the affected
                    activity is treated as cancelled. It is your responsibility to seek medical advice about
                    fitness to take part where relevant.
                </p>

                <h2>12. Travel insurance</h2>
                <p>
                    We strongly recommend that every member of the group holds travel insurance covering the
                    whole trip, including any adventurous or higher-risk activities booked. Where you have not
                    arranged suitable insurance, you take part in activities at your own risk.
                </p>

                <h2>13. Getting to the destination and meeting points</h2>
                <p>
                    We arrange your activities and, where booked, your accommodation — not your travel to the
                    destination or to activity meeting points. We are not responsible for a missed activity
                    caused by delayed or cancelled flights, other transport problems, or border or immigration
                    issues; those should be taken up with your carrier or travel insurer.
                </p>

                <h2>14. Local partners and substitutions</h2>
                <p>
                    Activities and accommodation are provided by independent local partners, whom we select
                    with care and who are responsible for delivering their service in line with local health
                    and safety requirements. As your booking agent we arrange the booking with them on your
                    behalf; the contract for each service is between you and the partner that provides it. Where
                    a specific activity becomes unavailable, we may arrange an equivalent alternative of similar
                    standard and value. Photographs and descriptions on the website are representative of the
                    experiences we arrange.
                </p>

                <h2>15. Our responsibility to you</h2>
                <p>
                    We will arrange your booking with reasonable care and skill. As a booking agent, we are
                    responsible for foreseeable loss or damage caused by our own breach of these Terms — for
                    example an error we make in handling your booking — but we are not responsible for the way a
                    local partner performs the activity or accommodation itself, which is the partner's
                    responsibility. We are also not responsible for loss or damage that is not foreseeable, that
                    is caused by you or a member of your group, that is caused by a third party unconnected with
                    arranging your booking, or that is caused by circumstances beyond our reasonable control
                    (including events of force majeure such as extreme weather, strikes, terrorism, natural
                    disaster, or government action). Our liability to you will not exceed the total price of
                    your booking. Nothing in these Terms limits liability that cannot be limited by law,
                    including liability for death or personal injury caused by negligence, or for fraud, or any
                    mandatory rights you have as a consumer.
                </p>

                <h2>16. Your consumer rights</h2>
                <p>
                    If you are a consumer, you have statutory rights under Czech and EU law that these Terms do
                    not affect. Please note that the statutory 14-day right of withdrawal for online purchases
                    generally does not apply to leisure activities booked for a specific date or period, as
                    permitted by law; your cancellation rights for such bookings are set out in our{' '}
                    <Link to="/refund-policy">Refund &amp; Cancellation Policy</Link>.
                </p>

                <h2>17. Complaints</h2>
                <p>
                    If something is not right during your trip, tell the local partner on site and our
                    representative straight away, so we have the chance to put it right there and then. If your
                    complaint cannot be resolved locally, please email us within 28 days of the activity date
                    at <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a>, quoting your
                    booking reference and any relevant details, and we will look into it promptly.
                </p>

                <h2>18. Photos and marketing</h2>
                <p>
                    We or our local partners may take photographs or video during activities that could feature
                    members of your group, and we may use this material to promote our services. We will not use
                    material we would consider embarrassing. If you would prefer not to appear, let us know and
                    we will avoid using identifiable images of you. This is explained further in our{' '}
                    <Link to="/privacy-policy">Privacy Policy</Link>.
                </p>

                <h2>19. Intellectual property</h2>
                <p>
                    All content on the {COMPANY.tradeName} website — including text, images, logos, and design —
                    is owned by us or our licensors and is protected by law. You may not copy or reuse it
                    without our permission, except as allowed for normal personal use of the website.
                </p>

                <h2>20. Governing law and disputes</h2>
                <p>
                    These Terms are governed by the laws of the {COMPANY.governingLaw}, without affecting the
                    mandatory consumer-protection rules of your country of residence. We aim to resolve any
                    complaint directly — please contact us first at{' '}
                    <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a>. EU consumers may also
                    use the European Commission's Online Dispute Resolution platform at{' '}
                    <a href="https://ec.europa.eu/consumers/odr" target="_blank" rel="noopener noreferrer">
                        ec.europa.eu/consumers/odr
                    </a>.
                </p>

                <h2>21. Changes to these Terms</h2>
                <p>
                    We may update these Terms from time to time. The version in force at the time you make a
                    booking applies to that booking. The "Last updated" date above shows when they were last
                    changed.
                </p>

                <h2>22. Contact us</h2>
                <p>
                    Questions about these Terms? Email{' '}
                    <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a> or write to us at{' '}
                    {COMPANY.address}.
                </p>
            </section>
        </div>
    );
}

export default TermsPage;
