import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LogOut, Menu, Zap } from 'lucide-react';
import { useAuth } from '../store/AuthContext';
import ThemeToggle from '../components/common/ThemeToggle';
import NotificationBell from '../components/common/NotificationBell';
const menus = {
 admin: [['Overview','/admin'],['Accounts','/admin/accounts'],['Approvals','/admin/approvals'],['Assignments','/admin/assignments'],['Utilities','/admin/services'],['Records','/admin/records'],['Reports','/admin/reports']],
 operator: [['Overview','/operator'],['Customers','/operator/customers'],['Meter readings','/operator/readings'],['Bills','/operator/bills'],['LPG','/operator/lpg']],
 support: [['Overview','/support'],['Ticket queue','/support/tickets']],
 customer: [['Overview','/customer'],['My bills','/customer/bills'],['Usage','/customer/usage'],['Payments','/customer/payments'],['Connection requests','/customer/requests'],['Support','/customer/tickets']]
};
const titles={admin:'Administration',operator:'Operations',support:'Customer support',customer:'My utilities'};
export default function PortalLayout({ portal }) {
 const { user, logout }=useAuth(); const navigate=useNavigate();
 return <div className="min-h-screen bg-slate-50 md:flex">
  <aside className="hidden w-64 shrink-0 bg-slate-950 p-5 text-slate-100 md:block"><Link to={`/${portal}`} className="flex items-center gap-2 text-lg font-bold"><Zap className="text-teal-400"/>UtilityCare</Link><p className="mb-6 mt-8 text-xs uppercase tracking-widest text-slate-500">{titles[portal]}</p><nav className="space-y-1">{menus[portal].map(([label,path])=><NavLink key={path} end={path===`/${portal}`} to={path} className={({isActive})=>`block rounded-lg px-3 py-2.5 text-sm ${isActive?'bg-slate-800 text-white':'text-slate-400 hover:bg-slate-900 hover:text-white'}`}>{label}</NavLink>)}</nav></aside>
  <div className="min-w-0 flex-1"><header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-5"><div className="flex items-center gap-3"><Menu className="text-slate-500 md:hidden"/><span className="font-semibold text-slate-800">{titles[portal]}</span><span className="hidden text-slate-400 sm:inline">/ Overview</span></div><div className="flex items-center gap-3"><span className="hidden text-sm text-slate-600 sm:inline">{user?.email}</span><NotificationBell/><ThemeToggle/><button className="inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900" onClick={async()=>{await logout();navigate(`/${portal}/login`);}}><LogOut size={16}/>Sign out</button></div></header>
   <nav aria-label="Portal navigation" className="flex gap-2 overflow-x-auto border-b border-slate-200 bg-white px-4 py-2 md:hidden">{menus[portal].map(([label,path])=><NavLink key={path} end={path===`/${portal}`} to={path} className={({isActive})=>`whitespace-nowrap rounded-md px-3 py-2 text-xs font-medium ${isActive?'bg-slate-900 text-white':'text-slate-600 hover:bg-slate-100'}`}>{label}</NavLink>)}</nav>
   <main className="p-5 md:p-8"><Outlet/></main>
  </div>
 </div>;
}

