export default function FormInput({ label, error, className = '', ...props }) {
  return <label className="block text-sm font-medium text-slate-700">{label}<input className={`field mt-1.5 ${className}`} {...props}/>{error&&<span className="mt-1 block text-xs text-rose-600">{error}</span>}</label>;
}

