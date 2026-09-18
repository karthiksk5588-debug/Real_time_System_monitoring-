import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLab } from '../contexts/LabContext';

const Header = ({ mobileNavOpen, setMobileNavOpen }) => {
  const navigate = useNavigate();
  const { labs, currentLab, selectLab } = useLab();
  const [showLabDropdown, setShowLabDropdown] = useState(false);

  return (
    <header className="bg-surface text-primary font-headline-md text-headline-md w-full h-16 border-b border-outline-variant sticky top-0 right-0 flex items-center justify-between px-3 sm:px-gutter z-30 shrink-0 shadow-sm">
      <div className="flex items-center gap-2 sm:gap-4">
        {/* Mobile Hamburger Drawer Toggle */}
        <button
          onClick={() => setMobileNavOpen && setMobileNavOpen(!mobileNavOpen)}
          className="md:hidden p-2 rounded-lg border border-outline-variant bg-surface-container hover:bg-surface-container-high text-on-surface cursor-pointer flex items-center justify-center shrink-0"
          aria-label="Toggle navigation"
        >
          <span className="material-symbols-outlined text-[22px]">
            {mobileNavOpen ? 'close' : 'menu'}
          </span>
        </button>

        {/* Brand Anchor */}
        <div 
          onClick={() => navigate('/dashboard')}
          className="text-body-lg sm:text-headline-lg font-headline-lg font-black text-primary flex items-center gap-1.5 sm:gap-2 cursor-pointer shrink-0"
        >
          <span className="material-symbols-outlined icon-fill text-[24px] sm:text-[28px]">biotech</span>
          <span>NeuroSys</span>
        </div>

        {/* Contextual Lab Selector (Header Dropdown) */}
        <div className="relative ml-1 sm:ml-4">
          <div
            onClick={() => setShowLabDropdown(!showLabDropdown)}
            className="flex items-center gap-1.5 sm:gap-2 bg-surface-container-low border border-outline-variant rounded-lg px-2 sm:px-3 py-1 sm:py-1.5 cursor-pointer hover:border-primary transition-all shadow-sm"
          >
            <span className="material-symbols-outlined text-primary text-[16px] sm:text-[18px]">meeting_room</span>
            <span className="text-xs sm:text-body-md font-bold text-on-surface max-w-[100px] sm:max-w-none truncate">
              {currentLab?.name || 'Computer Lab 1'}
            </span>
            <span className="text-[9px] sm:text-[10px] font-bold bg-primary/10 text-primary px-1 sm:px-1.5 py-0.5 rounded">
              {currentLab?.code || 'LAB'}
            </span>
            <span className="material-symbols-outlined text-[16px] sm:text-[18px] text-secondary">expand_more</span>
          </div>

          {showLabDropdown && (
            <div className="absolute left-0 mt-1 w-64 bg-surface border border-outline-variant rounded-xl shadow-xl py-1 z-50 animate-fade-in-up">
              <div className="px-3 py-1.5 text-[10px] font-bold text-secondary uppercase border-b border-outline-variant">
                Select Computer Lab
              </div>
              {labs.map((lab) => (
                <button
                  key={lab.id}
                  onClick={() => {
                    selectLab(lab);
                    setShowLabDropdown(false);
                  }}
                  className={`w-full text-left px-4 py-2 text-xs font-bold flex items-center justify-between transition-colors ${
                    currentLab?.id === lab.id ? 'bg-surface-container text-primary border-l-2 border-primary' : 'text-on-surface hover:bg-surface-container-high'
                  }`}
                >
                  <span>{lab.name}</span>
                  <span className="text-[10px] font-mono font-bold bg-surface-container-highest px-1.5 py-0.5 rounded">
                    {lab.code}
                  </span>
                </button>
              ))}
              <div className="border-t border-outline-variant pt-1 mt-1">
                <button
                  onClick={() => {
                    selectLab({ id: 'ALL', name: 'All Laboratories', code: 'ALL' });
                    setShowLabDropdown(false);
                  }}
                  className="w-full text-left px-4 py-2 text-xs font-bold text-slate-600 hover:bg-surface-container-high"
                >
                  🌐 All Laboratories (Super Admin)
                </button>
                <button
                  onClick={() => {
                    setShowLabDropdown(false);
                    navigate('/select-lab');
                  }}
                  className="w-full text-left px-4 py-2 text-xs font-bold text-primary hover:bg-surface-container-high border-t border-outline-variant mt-1"
                >
                  🏢 Open Lab Selection Cards →
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="flex items-center gap-1.5 sm:gap-2">
        {/* Lab Management Quick Button */}
        <button
          onClick={() => navigate('/labs')}
          className="flex items-center gap-1 sm:gap-1.5 px-2.5 sm:px-3 py-1.5 rounded-lg border border-primary/20 bg-primary/5 hover:bg-primary/10 text-primary text-xs font-bold transition-all cursor-pointer"
        >
          <span className="material-symbols-outlined text-[16px] sm:text-[18px]">add_circle</span>
          <span className="hidden sm:inline">Manage Labs</span>
        </button>

        {/* Search Action */}
        <button
          onClick={() => navigate('/computers')}
          className="flex items-center gap-1.5 sm:gap-2 px-2.5 sm:px-3.5 py-1.5 rounded-full text-secondary hover:bg-secondary-container transition-all font-label-md text-xs font-bold"
        >
          <span className="material-symbols-outlined text-[18px] sm:text-[20px]">search</span>
          <span className="hidden sm:inline">Search Workstations</span>
        </button>
      </div>
    </header>
  );
};

export default Header;
