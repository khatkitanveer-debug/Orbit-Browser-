import { memo } from "react";
import { Favicon } from "./Favicon";
import { IconMusic, IconNews, IconPlay, IconVideo } from "../../components/chrome/Icons";
import { relativeTime, type FeedCard, type FeedResult } from "./feedEngine";

interface DiscoverFeedProps {
  feed: FeedResult;
  onOpen: (url: string) => void;
}

/**
 * Chrome-Discover-style stream, generated from local history only.
 * Cards are memoized per-item — a new visit re-renders just the changed
 * cards, not the whole stream.
 */
export function DiscoverFeed({ feed, onOpen }: DiscoverFeedProps) {
  return (
    <section className="feed" aria-label="Discover">
      <header className="feed__head">
        <h2 className="feed__title">Discover</h2>
        <p className="feed__sub">From your history — stays on your device</p>
      </header>
      <div className="feed__list">
        {feed.cards.map((card) => (
          <FeedCardView key={card.id} card={card} onOpen={onOpen} />
        ))}
      </div>
    </section>
  );
}

const FeedCardView = memo(function FeedCardView({
  card,
  onOpen,
}: {
  card: FeedCard;
  onOpen: (url: string) => void;
}) {
  return (
    <article className={`feed-card feed-card--${card.category}`}>
      {card.category === "video" ? (
        <button className="feed-card__hit feed-card__hit--media" onClick={() => onOpen(card.url)}>
          <Thumb card={card} />
          <span className="feed-card__body">
            <span className="feed-card__title feed-card__title--clamp2">{card.title}</span>
            <span className="feed-card__meta">
              <IconVideo size={13} />
              {prettyHost(card.host)} · {relativeTime(card.lastVisit)}
            </span>
          </span>
        </button>
      ) : card.category === "music" ? (
        <button className="feed-card__hit feed-card__hit--media" onClick={() => onOpen(card.url)}>
          <span className="feed-card__glyph feed-card__glyph--music" aria-hidden>
            <IconMusic size={22} />
          </span>
          <span className="feed-card__body">
            <span className="feed-card__title feed-card__title--clamp2">{card.title}</span>
            <span className="feed-card__meta">
              <IconMusic size={13} />
              {prettyHost(card.host)} · {relativeTime(card.lastVisit)}
            </span>
          </span>
        </button>
      ) : (
        <div className="feed-card__hit feed-card__hit--stack">
          <button className="feed-card__main" onClick={() => onOpen(card.url)}>
            <span className="feed-card__title feed-card__title--clamp3">{card.title}</span>
            <span className="feed-card__meta">
              <span className="feed-card__source">
                <IconNews size={12} />
                {prettyHost(card.host)}
              </span>
              <span className="feed-card__time">{relativeTime(card.lastVisit)}</span>
            </span>
          </button>
          <button className="feed-card__more" onClick={() => onOpen(card.homeUrl)}>
            More from {prettyHost(card.host)}
          </button>
        </div>
      )}
    </article>
  );
});

/** Real YouTube thumbnail when we have an id; gradient play-tile otherwise. */
function Thumb({ card }: { card: FeedCard }) {
  if (card.videoId) {
    return (
      <span className="feed-card__thumb">
        <img
          src={`https://i.ytimg.com/vi/${card.videoId}/mqdefault.jpg`}
          alt=""
          loading="lazy"
          decoding="async"
          onError={(e) => e.currentTarget.classList.add("feed-card__thumb-img--hidden")}
        />
        <span className="feed-card__play" aria-hidden>
          <IconPlay size={16} />
        </span>
      </span>
    );
  }
  return (
    <span className="feed-card__thumb feed-card__thumb--bare" aria-hidden>
      <Favicon host={card.host} size={26} />
    </span>
  );
}

function prettyHost(host: string): string {
  return host.replace(/^www\./, "");
}
