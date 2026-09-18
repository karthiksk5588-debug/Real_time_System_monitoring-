import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import AIAssistantWidget from './AIAssistantWidget';

const Layout = () => {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  return (
    <div className="flex min-h-screen bg-background text-on-background font-body-md antialiased selection:bg-primary-container selection:text-white relative">
      <Sidebar mobileNavOpen={mobileNavOpen} setMobileNavOpen={setMobileNavOpen} />
      <div className="flex-1 ml-0 md:ml-sidebar-width flex flex-col min-w-0 h-screen overflow-hidden">
        <Header mobileNavOpen={mobileNavOpen} setMobileNavOpen={setMobileNavOpen} />
        <main className="flex-1 overflow-y-auto p-3 sm:p-4 md:p-container-padding">
          <Outlet />
        </main>
      </div>
      <AIAssistantWidget />
    </div>
  );
};

export default Layout;
