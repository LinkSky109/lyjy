import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './theme.css';
import './styles.css';

const saved = localStorage.getItem('lhjypt.theme');
document.documentElement.dataset.theme = saved === 'dark' ? 'dark' : 'light';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
