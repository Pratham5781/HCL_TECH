import Modal from './Modal';
export default function ConfirmDialog({ open, title = 'Confirm action', message, onCancel, onConfirm, busy = false }) {
  return <Modal open={open} title={title} onClose={onCancel}><p className="text-sm leading-6 text-slate-600">{message}</p><div className="mt-6 flex justify-end gap-3"><button disabled={busy} onClick={onCancel} className="rounded-lg border px-4 py-2 text-sm">Cancel</button><button disabled={busy} onClick={onConfirm} className="rounded-lg bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">{busy?'Saving…':'Confirm'}</button></div></Modal>;
}

