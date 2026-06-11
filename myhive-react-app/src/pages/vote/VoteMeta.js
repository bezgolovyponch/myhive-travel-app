import {Helmet} from 'react-helmet-async';

// Vote pages are shared via tokenized links pasted into group chats: give the
// tab a real title and keep the token URLs out of search indexes.
function VoteMeta({title}) {
    return (
        <Helmet>
            <title>{title} — Trivlu</title>
            <meta name="robots" content="noindex"/>
        </Helmet>
    );
}

export default VoteMeta;
