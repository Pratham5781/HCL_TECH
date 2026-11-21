import { X } from 'lucide-react';
export default function Modal({ open, title, onClose, children, wide = false }) {
  if (!open) return null;
  return <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4" role="presentation" onMouseDown={e => { if(e.target === e.currentTarget) onClose(); }}>
    <section role="dialog" aria-modal="true" aria-label={title} className={`max-h-[90vh] w-full overflow-y-auto rounded-2xl bg-white p-6 shadow-xl ${wide ? 'max-w-3xl' : 'max-w-lg'}`}>
      <header className="mb-5 flex items-center justify-between gap-3"><h2 className="text-lg font-semibold text-slate-900">{title}</h2><button type="button" aria-label="Close dialog" onClick={onClose} className="rounded-lg p-2 text-slate-500 hover:bg-slate-100"><X size={18}/></button></header>{children}
    </section>
  </div>;
}

