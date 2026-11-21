import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
export default function DataTable({ columns, rows, page = 0, size = 20, total = 0, onPage, onSort, sort, direction = 'DESC', empty = 'No records found', showPagination = true }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
    <div className="overflow-x-auto"><table className="w-full border-collapse text-left text-sm">
      <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr>{columns.map(c => <th key={c.key} className="whitespace-nowrap px-4 py-3">{c.sortable ? <button className="inline-flex items-center gap-1" onClick={() => onSort?.(c.key)}>{c.label}{sort === c.key ? direction === 'ASC' ? <ArrowUp size={13}/> : <ArrowDown size={13}/> : <ChevronsUpDown size={13}/>}</button> : c.label}</th>)}</tr></thead>
      <tbody className="divide-y divide-slate-100">{rows.length ? rows.map((row,i)=><tr key={row.id ?? row.userId ?? i} className="hover:bg-slate-50/70">{columns.map(c=><td key={c.key} className="px-4 py-3 align-middle text-slate-700">{c.render ? c.render(row) : row[c.key] ?? '—'}</td>)}</tr>) : <tr><td colSpan={columns.length} className="px-4 py-12 text-center text-slate-500">{empty}</td></tr>}</tbody>
    </table></div>
    {showPagination&&<div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 px-4 py-3 text-sm text-slate-500"><span>{total === 0 ? '0 records' : `${page * size + 1}–${Math.min((page + 1) * size, total)} of ${total}`}</span><div className="flex gap-2"><button disabled={page <= 0} onClick={() => onPage?.(page - 1)} className="rounded-md border px-3 py-1.5 disabled:opacity-40">Previous</button><span className="px-2 py-1.5">Page {page + 1} of {pages}</span><button disabled={page + 1 >= pages} onClick={() => onPage?.(page + 1)} className="rounded-md border px-3 py-1.5 disabled:opacity-40">Next</button></div></div>}
  </div>;
}

