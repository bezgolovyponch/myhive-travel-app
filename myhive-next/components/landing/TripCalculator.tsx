'use client';

// "See what your budget actually buys" — the example-itinerary calculator with
// its sticky controls, plus the home-city price comparison that follows the
// chosen trip length. All maths lives in engine.ts; this file only renders.
import { useEffect, useMemo, useRef, useState } from 'react';
import { useT, useLocalePath } from '../../legacy-src/i18n';
import {
  buildLadder,
  compareCities,
  CITIES,
  TRANSFER,
  type Pool,
  type Programme,
} from './engine';
import { builderUrl, poolImageUrl } from './data';
import { formatAmount } from '../../legacy-src/utils/format';
import { trackCtaAndGo } from './analytics';

const ARRIVE_PH = '/landing/arrive.webp';
const DEPART_PH =
  'https://wsrv.nl/?url=https%3A%2F%2Fimages.pexels.com%2Fphotos%2F28887086%2Fpexels-photo-28887086%2Ffree-photo-of-easyjet-airplane-taking-off-at-prague-airport.jpeg%3Fauto%3Dcompress%26dpr%3D1%26w%3D320&w=96&output=webp&q=70';

const DAY_OPTIONS = [1, 2, 3];
const PER_DAY_OPTIONS = [2, 3, 4];

function ItineraryRows({ programme, prevNames }: { programme: Programme; prevNames: string[] }) {
  const t = useT('landing.calc');
  const { sl, chosen } = programme;
  const rows: React.ReactNode[] = [];
  let lastDay = '';
  sl.forEach((s, i) => {
    if (s.day !== lastDay) {
      rows.push(
        <div className="tc-day" key={`day-${s.day}`}>
          {t(`day${s.day}`)}
        </div>,
      );
      if (lastDay === '') {
        rows.push(
          <div className="tc-row tc-row--fix" key="arrive">
            <span className="tc-row__t">16:40</span>
            <img className="tc-row__ph" src={ARRIVE_PH} alt="" aria-hidden="true" loading="lazy" decoding="async" />
            <span className="tc-row__n">{t('airportTransfer')}</span>
            <span className="tc-row__p">{formatAmount(TRANSFER.p)}</span>
          </div>,
        );
      }
      lastDay = s.day;
    }
    const isNew = prevNames.length === chosen.length && prevNames[i] !== chosen[i].n;
    rows.push(
      <div className={`tc-row${isNew ? ' is-new' : ''}`} key={`${s.day}-${s.time}`}>
        <span className="tc-row__t">{s.time}</span>
        <img
          className="tc-row__ph"
          src={poolImageUrl(chosen[i])}
          alt=""
          aria-hidden="true"
          loading="lazy"
          decoding="async"
        />
        <span className="tc-row__n">{chosen[i].n}</span>
        <span className="tc-row__p">{formatAmount(chosen[i].p)}</span>
      </div>,
    );
  });
  rows.push(
    <div className="tc-row tc-row--fix" key="depart">
      <span className="tc-row__t">16:00</span>
      <img className="tc-row__ph" src={DEPART_PH} alt="" aria-hidden="true" loading="lazy" decoding="async" />
      <span className="tc-row__n">{t('airportTransfer')}</span>
      <span className="tc-row__p">{formatAmount(TRANSFER.p)}</span>
    </div>,
  );
  return <div>{rows}</div>;
}

function CityCompare({ nights }: { nights: number }) {
  const t = useT('landing.compare');
  const [homeCity, setHomeCity] = useState('Oslo');
  const c = compareCities(homeCity, nights);
  return (
    <div className="cost" style={{ marginTop: '2.5rem', gridTemplateColumns: '1fr' }}>
      <div>
        <div className="vs">
          <div className="vs__hd">
            {t('header')}{' '}
            <small>{t('sub', { drinks: c.drinks, nights: c.nights, meals: c.meals })}</small>
          </div>
          <div className="vs__bd">
            <div className="cityrow">
              <span className="cityrow__l">{t('compareWith')}</span>
              {Object.keys(CITIES).map((city) => (
                <button
                  key={city}
                  type="button"
                  className={`citychip${city === homeCity ? ' is-on' : ''}`}
                  onClick={() => setHomeCity(city)}
                >
                  {city}
                </button>
              ))}
            </div>
            <div className="vs__num">
              <div className="vsrow vsrow--home">
                <span className="vsrow__c">
                  {homeCity}{' '}
                  <i>{t('breakdown', { beer: c.home.beer, bed: c.home.bed, food: c.home.food })}</i>
                </span>
                <span className="vsrow__v">{formatAmount(c.home.total)}</span>
                <span className="vsrow__t">
                  <span className="vsrow__f" style={{ width: '100%' }} />
                </span>
              </div>
              <div className="vsrow vsrow--prg">
                <span className="vsrow__c">
                  {t('prague')}{' '}
                  <i>
                    {t('breakdown', { beer: c.prague.beer, bed: c.prague.bed, food: c.prague.food })}
                  </i>
                </span>
                <span className="vsrow__v">{formatAmount(c.prague.total)}</span>
                <span className="vsrow__t">
                  <span
                    className="vsrow__f"
                    style={{ width: `${Math.round((c.prague.total / c.home.total) * 100)}%` }}
                  />
                </span>
              </div>
            </div>
            {/* the numbers, and what they buy */}
            <figure className="joy">
              <img
                alt={t('joyAlt')}
                loading="lazy"
                decoding="async"
                src="https://wsrv.nl/?url=https%3A%2F%2Fimages.pexels.com%2Fphotos%2F3851421%2Fpexels-photo-3851421.jpeg%3Fauto%3Dcompress%26cs%3Dtinysrgb%26w%3D1200&w=900&output=webp&q=76"
              />
            </figure>
            <div className="vs__save">
              <span>{t('saves')}</span>
              <b>{formatAmount(c.groupSaves)}</b>
            </div>
          </div>
          <p className="vs__src">{t('source')}</p>
        </div>
      </div>
    </div>
  );
}

export default function TripCalculator({
  pool,
  destinationSlug,
}: {
  pool: Pool;
  destinationSlug: string;
}) {
  const t = useT('landing.calc');
  const lp = useLocalePath();
  const [days, setDays] = useState(2);
  const [perDay, setPerDay] = useState(3);
  // The slider keeps its relative position when the ladder is rebuilt, exactly
  // as the mockup preserves the drag percentage across day/count changes.
  const [pct, setPct] = useState(0.5);
  const prevNamesRef = useRef<string[]>([]);

  const ladder = useMemo(() => buildLadder(pool, days, perDay), [pool, days, perDay]);
  const index = Math.min(Math.round((ladder.length - 1) * pct), ladder.length - 1);
  const programme = ladder[index];

  const prevNames = prevNamesRef.current;
  useEffect(() => {
    prevNamesRef.current = programme.chosen.map((c) => c.n);
  }, [programme]);

  // On mobile the controls return inside the result card (bottom-sticky);
  // on desktop they are the sticky right-hand sidebar.
  const [mobile, setMobile] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia('(max-width:900px)');
    const sync = () => setMobile(mq.matches);
    sync();
    mq.addEventListener('change', sync);
    return () => mq.removeEventListener('change', sync);
  }, []);

  const builderHref = lp(builderUrl(destinationSlug));
  const controls = (
    <aside className="tc-ctrl">
      <div className="tc-field">
        <span className="tc-field__l">{t('howManyDays')}</span>
        <span className="tc-seg">
          {DAY_OPTIONS.map((d) => (
            <button
              key={d}
              type="button"
              className={d === days ? 'is-on' : undefined}
              onClick={() => setDays(d)}
            >
              {d}
            </button>
          ))}
        </span>
      </div>
      <div className="tc-field">
        <span className="tc-field__l">{t('activitiesPerDay')}</span>
        <span className="tc-seg">
          {PER_DAY_OPTIONS.map((p) => (
            <button
              key={p}
              type="button"
              className={p === perDay ? 'is-on' : undefined}
              onClick={() => setPerDay(p)}
            >
              {p}
            </button>
          ))}
        </span>
      </div>
      <div className="tc-slab">
        <span>{t('budgetPerPerson')}</span>
        <b>{formatAmount(programme.spent)}</b>
      </div>
      <input
        aria-label={t('budgetPerPerson')}
        type="range"
        min={0}
        max={ladder.length - 1}
        step={1}
        value={index}
        onChange={(e) => setPct(Number(e.target.value) / Math.max(ladder.length - 1, 1))}
      />
      <div className="tc-sends">
        <span>{formatAmount(ladder[0].spent)}</span>
        <span>{formatAmount(ladder[ladder.length - 1].spent)}</span>
      </div>
      <a
        className="btn btn--primary btn--block tc-ctrl__cta"
        href={builderHref}
        onClick={(e) => {
          e.preventDefault();
          trackCtaAndGo('Build your trip now', 'costs', builderHref);
        }}
      >
        {t('cta')}
      </a>
    </aside>
  );

  return (
    <>
      <div className="tripcalc">
        <div className="tripcalc__card">
          <div className="tc-hd">
            <h3>{days === 2 ? t('exampleWeekend') : t('exampleTrip', { days })}</h3>
            <small>{t('activitiesCount', { count: programme.sl.length })}</small>
          </div>
          <p className="tc-sub">{t('sub')}</p>
          <ItineraryRows programme={programme} prevNames={prevNames} />
          <div className="tc-tot">
            <span>{t('perPerson')}</span>
            <b>{formatAmount(programme.spent)}</b>
          </div>
          <p className="tc-note">{t('note')}</p>
          {mobile ? controls : null}
        </div>
        {mobile ? null : controls}
      </div>
      <CityCompare nights={days} />
    </>
  );
}
