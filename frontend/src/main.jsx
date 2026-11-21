import React, { lazy, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import './styles.css';
import { AuthProvider } from './store/AuthContext';
import { ProtectedRoute, RoleRoute } from './routes/ProtectedRoute';
import LoginPage from './pages/auth/LoginPage';
import PortalLayout from './layouts/PortalLayout';
import PortalHome from './pages/PortalHome';
import RegisterPage from './pages/auth/RegisterPage';
import { ForgotPasswordPage, ResetPasswordPage } from './pages/auth/RecoveryPages';
const AdminDashboard=lazy(()=>import('./pages/admin/AdminDashboard'));
const AdminAccountsPage=lazy(()=>import('./pages/admin/AdminAccountsPage'));
const AdminApprovalsPage=lazy(()=>import('./pages/admin/AdminApprovalsPage'));
const AdminAssignmentsPage=lazy(()=>import('./pages/admin/AdminAssignmentsPage'));
const AdminServicesPage=lazy(()=>import('./pages/admin/AdminServicesPage'));
const AdminRecordsPage=lazy(()=>import('./pages/admin/AdminRecordsPage'));
const AdminReportsPage=lazy(()=>import('./pages/admin/AdminReportsPage'));
const OperatorDashboard=lazy(()=>import('./pages/operator/OperatorDashboard'));
const OperatorCustomersPage=lazy(()=>import('./pages/operator/OperatorCustomersPage'));
const OperatorReadingsPage=lazy(()=>import('./pages/operator/OperatorReadingsPage'));
const OperatorBillsPage=lazy(()=>import('./pages/operator/OperatorBillsPage'));
const OperatorLpgPage=lazy(()=>import('./pages/operator/OperatorLpgPage'));
const CustomerDashboard=lazy(()=>import('./pages/customer/CustomerDashboard'));
const CustomerBillsPage=lazy(()=>import('./pages/customer/CustomerBillsPage'));
const CustomerPaymentsPage=lazy(()=>import('./pages/customer/CustomerPaymentsPage'));
const CustomerUsagePage=lazy(()=>import('./pages/customer/CustomerUsagePage'));
const CustomerRequestsPage=lazy(()=>import('./pages/customer/CustomerRequestsPage'));
const SupportDashboard=lazy(()=>import('./pages/support/SupportDashboard'));
const SupportTicketsPage=lazy(()=>import('./pages/support/SupportTicketsPage'));
const SupportTicketDetailPage=lazy(()=>import('./pages/support/SupportTicketDetailPage'));
const SupportCustomerPage=lazy(()=>import('./pages/support/SupportCustomerPage'));
const CustomerTicketsPage=lazy(()=>import('./pages/customer/CustomerTicketsPage'));
function LazyPage({children}){return <Suspense fallback={<div className="card">Loading administration page…</div>}>{children}</Suspense>}

const portals=[['admin','ADMIN'],['operator','OPERATOR'],['support','HELP_SUPPORT'],['customer','CUSTOMER']];
function App(){
 return <AuthProvider><BrowserRouter><Routes>
  <Route path="/" element={<LoginPage portal="chooser"/>}/>
  {portals.map(([path,role])=><Route key={path} path={`/${path}/login`} element={<LoginPage portal={path}/>}/>)}
  <Route path="/customer/register" element={<RegisterPage/>}/>
  <Route path="/forgot-password" element={<ForgotPasswordPage/>}/>
  <Route path="/reset-password" element={<ResetPasswordPage/>}/>
  <Route element={<ProtectedRoute/>}>{portals.map(([path,role])=><Route key={path} element={<RoleRoute role={role}/>}>
    <Route path={`/${path}`} element={<PortalLayout portal={path}/>}>
      {path==='admin'?<>
       <Route index element={<LazyPage><AdminDashboard/></LazyPage>}/><Route path="accounts" element={<LazyPage><AdminAccountsPage/></LazyPage>}/><Route path="approvals" element={<LazyPage><AdminApprovalsPage/></LazyPage>}/><Route path="assignments" element={<LazyPage><AdminAssignmentsPage/></LazyPage>}/><Route path="services" element={<LazyPage><AdminServicesPage/></LazyPage>}/><Route path="records" element={<LazyPage><AdminRecordsPage/></LazyPage>}/><Route path="reports" element={<LazyPage><AdminReportsPage/></LazyPage>}/>
       <Route path="*" element={<section className="card"><h1 className="text-xl font-bold">Page not found</h1><p className="mt-2 text-slate-500">The requested page does not exist in this portal.</p></section>}/>
      </>:path==='operator'?<>
       <Route index element={<LazyPage><OperatorDashboard/></LazyPage>}/><Route path="customers" element={<LazyPage><OperatorCustomersPage/></LazyPage>}/><Route path="readings" element={<LazyPage><OperatorReadingsPage/></LazyPage>}/><Route path="bills" element={<LazyPage><OperatorBillsPage/></LazyPage>}/><Route path="lpg" element={<LazyPage><OperatorLpgPage/></LazyPage>}/>
       <Route path="*" element={<section className="card"><h1 className="text-xl font-bold">Page not found</h1><p className="mt-2 text-slate-500">The requested page does not exist in this portal.</p></section>}/>
      </>:path==='support'?<>
       <Route index element={<LazyPage><SupportDashboard/></LazyPage>}/><Route path="tickets" element={<LazyPage><SupportTicketsPage/></LazyPage>}/><Route path="tickets/:id" element={<LazyPage><SupportTicketDetailPage/></LazyPage>}/><Route path="customers/:id" element={<LazyPage><SupportCustomerPage/></LazyPage>}/>
       <Route path="*" element={<section className="card"><h1 className="text-xl font-bold">Page not found</h1><p className="mt-2 text-slate-500">The requested page does not exist in this portal.</p></section>}/>
      </>:path==='customer'?<>
       <Route index element={<LazyPage><CustomerDashboard/></LazyPage>}/><Route path="bills" element={<LazyPage><CustomerBillsPage/></LazyPage>}/><Route path="payments" element={<LazyPage><CustomerPaymentsPage/></LazyPage>}/><Route path="usage" element={<LazyPage><CustomerUsagePage/></LazyPage>}/><Route path="requests" element={<LazyPage><CustomerRequestsPage/></LazyPage>}/><Route path="tickets" element={<LazyPage><CustomerTicketsPage/></LazyPage>}/>
       <Route path="*" element={<section className="card"><h1 className="text-xl font-bold">Page not found</h1><p className="mt-2 text-slate-500">The requested page does not exist in this portal.</p></section>}/>
      </>:<><Route index element={<PortalHome portal={path}/>}/><Route path="*" element={<section className="card"><h1 className="text-xl font-bold">Page not found</h1><p className="mt-2 text-slate-500">The requested page does not exist in this portal.</p></section>}/></>}
    </Route>
   </Route>)}</Route>
  <Route path="*" element={<Navigate to="/" replace/>}/>
 </Routes><Toaster position="top-right" toastOptions={{duration:3500}}/></BrowserRouter></AuthProvider>
}
createRoot(document.getElementById('root')).render(<React.StrictMode><App/></React.StrictMode>);

