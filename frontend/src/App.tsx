import { HealthCard, type HealthCardProps } from './components/HealthCard'
import {useEffect, useState} from "react";
import './App.css'
import {fetchHealth} from "./api/health.ts";

export default function App() {
    const [health, setHealth] = useState<HealthCardProps>({ state: 'loading' });

    useEffect( () => {
        fetchHealth()
            .then((data) => setHealth({ state: 'loaded', data: data }))
            .catch(() =>
                setHealth({
                    state: 'error',
                    message: 'Could not reach the CRM API. Is the backend running?',
                }),
            )
        }, []); // runs once on mount

  return (
    <main className="app">
      <header className="app__header">
        <h1 className="app__title">CRM</h1>
        <p className="app__subtitle">Service status</p>
      </header>

      <HealthCard {...health} />

      <footer className="app__footer">M1 · scaffolding &amp; health check</footer>
    </main>
  )
}
