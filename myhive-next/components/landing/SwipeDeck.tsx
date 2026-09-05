'use client';

// The hero's signature element: a live swipe deck. Drag (or the labelled
// buttons, or arrow keys) sorts eight activities into a demo shortlist; the
// end state hands off to the real trip builder. Drag transforms are applied
// imperatively to the top card node — React re-renders the stack after each
// swipe, which resets them for free.
import { useEffect, useRef, useState } from 'react';
import { useT, useLocalePath } from '../../legacy-src/i18n';
import { formatAmount, formatDuration } from '../../legacy-src/utils/format';
import type { LandingActivity } from './data';
import { builderUrlWithPicks } from './data';
import type { DeckState, DeckAction } from './deck';
import { isDeckFinished } from './deck';
import { trackCta, trackCtaAndGo } from './analytics';

const FLY_MS = 300;
const SWIPE_THRESHOLD_PX = 95;

function Coach({ off }: { off: boolean }) {
  const t = useT('landing.vote.deck');
  return (
    <div className={`coach${off ? ' is-off' : ''}`}>
      <svg
        className="coach__ico"
        viewBox="0 0 48 48"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <g className="arw arw--l">
          <path d="M19 8H6" />
          <path d="M11 3 6 8l5 5" />
        </g>
        <g className="arw arw--r">
          <path d="M29 8h13" />
          <path d="m37 3 5 5-5 5" />
        </g>
        <path d="M18 30V15a3 3 0 0 1 6 0v11" />
        <path d="M24 26v-3a3 3 0 0 1 6 0v3" />
        <path d="M30 26a3 3 0 0 1 6 0v8a10 10 0 0 1-10 10h-3a10 10 0 0 1-10-10v-6a3 3 0 0 1 5.2-2l1.8 2" />
      </svg>
      <p className="coach__t">{t('coach')}</p>
    </div>
  );
}

function Shortlist({
  deck,
  picked,
  destinationSlug,
}: {
  deck: LandingActivity[];
  picked: string[];
  destinationSlug: string;
}) {
  const t = useT('landing.vote.deck');
  const lp = useLocalePath();
  const chosen = deck.filter((a) => picked.includes(a.slug));
  const href = lp(builderUrlWithPicks(destinationSlug, picked));
  const ctaLabel = chosen.length ? 'Build your trip now' : 'Build your own trip';
  return (
    <div className="short">
      <div className="short__hd">
        <h3>{t('shortTitle')}</h3>
        <span className="num">{t('chosen', { count: chosen.length })}</span>
      </div>
      {chosen.length ? (
        <ul className="short__list">
          {chosen.map((a) => (
            <li key={a.slug}>
              <img src={a.imageUrl} alt="" />
              <span className="short__nm">{a.name}</span>
              <span className="short__pr">{formatAmount(a.price)}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="short__empty">{t('empty')}</p>
      )}
      <a
        className="btn btn--primary btn--block"
        href={href}
        onClick={(e) => {
          e.preventDefault();
          trackCtaAndGo(ctaLabel, 'deck', href);
        }}
      >
        {chosen.length ? t('buildNow') : t('buildOwn')}
      </a>
      <a
        className="btn btn--ghost btn--block"
        style={{ marginTop: '.6rem' }}
        href="#activities"
        onClick={() => trackCta('Explore activities', 'deck')}
      >
        {t('explore')}
      </a>
    </div>
  );
}

export default function SwipeDeck({
  deck,
  state,
  dispatch,
  picked,
  destinationSlug,
}: {
  deck: LandingActivity[];
  state: DeckState;
  dispatch: (a: DeckAction) => void;
  // Slugs already in the cart. Passed in rather than read off DeckState: the
  // picks live in TripContext now, so the deck no longer knows them.
  picked: string[];
  destinationSlug: string;
}) {
  const t = useT('landing.vote.deck');
  const tAct = useT('landing.activities');
  const tDuration = useT('activityDetail.duration');
  const stageRef = useRef<HTMLDivElement>(null);
  const busyRef = useRef(false);
  const [touched, setTouched] = useState(false);
  const touchedRef = useRef(false);

  const finished = isDeckFinished(state, deck.length);

  const hideHint = () => {
    if (!touchedRef.current) {
      touchedRef.current = true;
      setTouched(true);
    }
  };

  const topCard = (): HTMLElement | null =>
    stageRef.current?.querySelector<HTMLElement>('.dcard:last-child') ?? null;

  const fly = (el: HTMLElement, id: string, isYes: boolean) => {
    busyRef.current = true;
    hideHint();
    el.style.transition = 'transform .38s ease-out, opacity .38s ease-out';
    el.style.transform = `translate(${isYes ? 700 : -700}px, ${isYes ? -60 : 60}px) rotate(${isYes ? 26 : -26}deg)`;
    el.style.opacity = '0';
    setTimeout(() => {
      busyRef.current = false;
      dispatch({ type: 'swipe', yes: isYes, id });
    }, FLY_MS);
  };

  const swipeTop = (isYes: boolean) => {
    if (busyRef.current || state.cursor >= deck.length) return;
    const el = topCard();
    if (!el) return;
    const stamp = el.querySelector<HTMLElement>(
      isYes ? '.dcard__stamp--yes' : '.dcard__stamp--no',
    );
    if (stamp) stamp.style.opacity = '1';
    fly(el, deck[state.cursor].slug, isYes);
  };

  // Drag on the top card. Handlers re-bind whenever the stack re-renders.
  const attachDrag = (el: HTMLDivElement | null, id: string) => {
    if (!el || el.dataset.dragBound) return;
    el.dataset.dragBound = '1';
    let sx = 0;
    let sy = 0;
    let dx = 0;
    let dy = 0;
    let down = false;
    const yes = el.querySelector<HTMLElement>('.dcard__stamp--yes')!;
    const no = el.querySelector<HTMLElement>('.dcard__stamp--no')!;
    el.addEventListener('pointerdown', (e) => {
      if (busyRef.current) return;
      hideHint();
      down = true;
      el.classList.add('is-drag');
      sx = e.clientX;
      sy = e.clientY;
      el.setPointerCapture(e.pointerId);
    });
    el.addEventListener('pointermove', (e) => {
      if (!down) return;
      dx = e.clientX - sx;
      dy = e.clientY - sy;
      el.style.transform = `translate(${dx}px, ${dy * 0.35}px) rotate(${dx * 0.05}deg)`;
      const k = Math.min(Math.abs(dx) / 110, 1);
      yes.style.opacity = dx > 0 ? String(k) : '0';
      no.style.opacity = dx < 0 ? String(k) : '0';
    });
    const end = () => {
      if (!down) return;
      down = false;
      el.classList.remove('is-drag');
      if (Math.abs(dx) > SWIPE_THRESHOLD_PX) {
        fly(el, id, dx > 0);
      } else {
        el.style.transition = 'transform .28s cubic-bezier(.2,.8,.3,1)';
        el.style.transform = 'translate(0,0) rotate(0)';
        yes.style.opacity = no.style.opacity = '0';
        setTimeout(() => {
          el.style.transition = '';
        }, 280);
      }
      dx = dy = 0;
    };
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
  };

  // Arrow keys drive the deck as long as cards remain.
  useEffect(() => {
    if (finished) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') swipeTop(true);
      if (e.key === 'ArrowLeft') swipeTop(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  });

  // If nobody touches it, the top card tilts both ways once — the deck
  // demonstrates itself, which reads faster than any label.
  useEffect(() => {
    if (finished || touched) return;
    let timer: ReturnType<typeof setTimeout>;
    const demo = () => {
      if (touchedRef.current) return;
      const top = topCard();
      if (!top) return;
      const yes = top.querySelector<HTMLElement>('.dcard__stamp--yes')!;
      const no = top.querySelector<HTMLElement>('.dcard__stamp--no')!;
      const set = (x: number, r: number, sy: number, sn: number) => {
        top.style.transition = 'transform .5s cubic-bezier(.3,.9,.3,1)';
        top.style.transform = `translateX(${x}px) rotate(${r}deg)`;
        yes.style.transition = no.style.transition = 'opacity .4s';
        yes.style.opacity = String(sy);
        no.style.opacity = String(sn);
      };
      set(56, 5, 0.85, 0);
      setTimeout(() => set(0, 0, 0, 0), 620);
      setTimeout(() => set(-56, -5, 0, 0.85), 1180);
      setTimeout(() => {
        set(0, 0, 0, 0);
        if (!touchedRef.current) timer = setTimeout(demo, 4200);
      }, 1800);
    };
    timer = setTimeout(demo, 1500);
    return () => clearTimeout(timer);
  }, [finished, touched]);

  if (finished) {
    return (
      <div className="deck">
        <div className="deck__stage" style={{ height: 'auto' }} id="deck-stage">
          <Shortlist deck={deck} picked={picked} destinationSlug={destinationSlug} />
        </div>
      </div>
    );
  }

  const slice = deck.slice(state.cursor, state.cursor + 3).reverse();

  return (
    <div className="deck">
      <div className="deck__stage" id="deck-stage" aria-label={t('stageAria')} ref={stageRef}>
        <span className="deck__count">
          <b>{Math.min(state.cursor + 1, deck.length)}</b> / <span>{deck.length}</span>
        </span>
        {!touched && <Coach off={touched} />}
        {slice.map((a, i) => {
          const depth = slice.length - 1 - i;
          const isTop = depth === 0;
          return (
            <div
              className="dcard"
              key={a.slug}
              data-slug={a.slug}
              ref={isTop ? (el) => attachDrag(el, a.slug) : undefined}
              style={{
                transform: `translateY(${depth * 9}px) scale(${1 - depth * 0.035})`,
                opacity: depth > 1 ? 0.5 : 1,
                zIndex: 10 - depth,
              }}
            >
              <img
                className="dcard__img"
                src={a.imageUrl}
                alt=""
                draggable={false}
                loading={state.cursor < 2 ? 'eager' : 'lazy'}
              />
              <div className="dcard__shade" />
              {a.category ? <span className="dcard__chip">{a.category}</span> : null}
              <span className="dcard__stamp dcard__stamp--yes">{t('stampYes')}</span>
              <span className="dcard__stamp dcard__stamp--no">{t('stampNo')}</span>
              <div className="dcard__body">
                <div className="dcard__name">{a.name}</div>
                <div className="dcard__meta">
                  <i>{formatDuration(a.duration, tDuration)}</i> ·{' '}
                  {a.hasGroupMin ? `${tAct('from')} ` : ''}
                  {formatAmount(a.price)} {tAct('perPerson')}
                </div>
                {a.minPrice ? (
                  <div className="dcard__min">{tAct('minPerGroup', { min: a.minPrice })}</div>
                ) : null}
              </div>
            </div>
          );
        })}
      </div>

      <div className="deck__ctrls">
        <span className="dctl dctl--no">
          <button
            type="button"
            className="dbtn dbtn--no"
            aria-label={t('skipAria')}
            onClick={() => {
              hideHint();
              swipeTop(false);
            }}
          >
            <svg viewBox="0 0 24 24" fill="none" strokeWidth="2.5" strokeLinecap="round">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
          <span className="dctl__l">{t('skip')}</span>
        </span>
        <span className="dctl dctl--yes">
          <button
            type="button"
            className="dbtn dbtn--yes"
            aria-label={t('addAria')}
            onClick={() => {
              hideHint();
              swipeTop(true);
            }}
          >
            <svg viewBox="0 0 24 24">
              <path d="M12 21s-8-4.9-8-10.4A4.8 4.8 0 0 1 12 7a4.8 4.8 0 0 1 8 3.6C20 16.1 12 21 12 21Z" />
            </svg>
          </button>
          <span className="dctl__l">{t('add')}</span>
        </span>
      </div>
    </div>
  );
}
