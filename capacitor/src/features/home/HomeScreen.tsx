import { OrbitMark, IconSearch } from "../../components/chrome/Icons";

interface HomeScreenProps {
  /** Opens the app bar omnibox in edit mode. */
  onSearchTap: () => void;
}

/**
 * Module 1 home: logo + search pill only.
 * Module 2 will extend this file with shortcuts (YouTube / JioSaavn / +)
 * and the history-driven feed — the layout below is built to take them.
 */
export function HomeScreen({ onSearchTap }: HomeScreenProps) {
  return (
    <main className="home">
      <div>
        <OrbitMark size={88} />
        <h1 className="home__word">Orbit</h1>
        <p className="home__tag">Fast. Private. Yours.</p>
      </div>
      <button className="home__search" onClick={onSearchTap} aria-label="Search or type URL">
        <IconSearch size={18} />
        Search or type URL
      </button>
    </main>
  );
}
