import { useEffect, useState } from 'react';
import { Moon, Sun } from 'lucide-react';

function setTheme(dark) {
  document.documentElement.classList.toggle('dark', dark);
  localStorage.setItem('theme', dark ? 'dark' : 'light');
}

export default function ThemeToggle() {
  const [dark, setDark] = useState(() => typeof window !== 'undefined' && (localStorage.getItem('theme') === 'dark' || (!localStorage.getItem('theme') && window.matchMedia('(prefers-color-scheme: dark)').matches)));
  useEffect(() => setTheme(dark), [dark]);
  return <button type="button" onClick={() => setDark(value => !value)} aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'} title={dark ? 'Switch to light theme' : 'Switch to dark theme'} className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-800"><span className="sr-only">{dark ? 'Switch to light theme' : 'Switch to dark theme'}</span>{dark ? <Sun size={18}/> : <Moon size={18}/>}</button>;
}

