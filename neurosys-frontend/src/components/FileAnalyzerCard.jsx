import React, { useState, useEffect } from 'react';
import { metricsService } from '../services/metricsService';
import { HardDrive, Trash2, Copy, Sparkles, FolderSearch, AlertCircle, ChevronDown, ChevronUp, CheckCircle } from 'lucide-react';

const FileAnalyzerCard = ({ computerId, report: propReport }) => {
  const [report, setReport] = useState(propReport || null);
  const [loading, setLoading] = useState(!propReport);
  const [showLargeFiles, setShowLargeFiles] = useState(false);
  const [showDuplicateGroups, setShowDuplicateGroups] = useState(false);

  useEffect(() => {
    if (!propReport && computerId) {
      fetchFileAnalysis();
    }
  }, [computerId, propReport]);

  const fetchFileAnalysis = async () => {
    try {
      const data = await metricsService.getFileAnalysis(computerId);
      if (data) {
        setReport(data.data || data);
      }
    } catch (e) {
      console.error('Error fetching file analysis', e);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="card-elevated rounded-xl p-8 border border-slate-200 text-center text-slate-700 text-body-md font-semibold">
        Performing storage analysis on workstation directories...
      </div>
    );
  }

  if (!report) {
    return (
      <div className="card-elevated rounded-xl p-8 border border-dashed border-slate-200 bg-slate-50 text-center text-slate-700 text-body-md font-semibold">
        This analysis is not available because the agent has not completed a file scan cycle yet.
      </div>
    );
  }

  const diskTotal = report.diskTotalCapacityGb || 0;
  const diskUsed = report.diskUsedGb || 0;
  const diskFree = report.diskFreeGb || 0;
  const diskUsagePct = diskTotal > 0 ? Math.round((diskUsed / diskTotal) * 100) : 0;

  const actualScannedGb = report.totalScannedSizeGb || 0;
  const filesExamined = report.filesExamined || 0;
  const filesAccessible = report.filesAccessible || 0;
  const filesSkipped = report.filesSkipped || 0;
  const scanStatus = report.scanStatus || 'COMPLETED';

  const largeFilesList = report.largeFilesList || [];
  const duplicateGroupsList = report.duplicateGroupsList || [];
  const scannedLocations = report.scannedLocations || [];

  return (
    <div className="card-elevated rounded-xl p-6 border border-slate-200 space-y-5">
      {/* Header: Title & Scan Audit Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pb-3 border-b border-slate-200">
        <div>
          <h3 className="text-headline-md font-headline-md text-slate-900 font-bold flex items-center gap-2">
            <HardDrive className="w-5 h-5 text-primary" /> Disk Storage & File System Analyzer
          </h3>
          <p className="text-xs font-medium text-slate-600 mt-0.5">
            Real file analysis across user workspace data & temporary cache locations.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span
            className={`text-xs font-extrabold px-3 py-1 rounded-full border ${
              scanStatus === 'COMPLETED_WITH_WARNINGS'
                ? 'bg-amber-100 text-amber-900 border-amber-300'
                : 'bg-emerald-100 text-emerald-900 border-emerald-300'
            }`}
          >
            {scanStatus === 'COMPLETED_WITH_WARNINGS' ? 'Scan Completed (Limited Access)' : 'Scan Completed'}
          </span>
        </div>
      </div>

      {/* Part 11: Distinct Disk Capacity vs Actual File Scan Summary */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Physical Disk Capacity Card */}
        <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
          <div className="flex justify-between items-center text-xs font-bold text-slate-700">
            <span className="uppercase tracking-wider">Physical Storage Capacity</span>
            <span className="text-slate-900 font-mono">{diskUsed} GB used / {diskTotal} GB total</span>
          </div>
          
          <div className="w-full bg-slate-200 h-2.5 rounded-full overflow-hidden">
            <div
              className={`h-full rounded-full ${diskUsagePct >= 90 ? 'bg-red-500' : diskUsagePct >= 80 ? 'bg-amber-500' : 'bg-primary'}`}
              style={{ width: `${Math.min(100, Math.max(2, diskUsagePct))}%` }}
            />
          </div>

          <div className="flex justify-between text-xs text-slate-600 font-medium pt-0.5">
            <span>Free Space: <strong className="text-slate-900">{diskFree} GB</strong></span>
            <span>Utilization: <strong className="text-slate-900">{diskUsagePct}%</strong></span>
          </div>
        </div>

        {/* Actual File Analysis Scope Audit */}
        <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
          <div className="flex justify-between items-center text-xs font-bold text-slate-700">
            <span className="uppercase tracking-wider flex items-center gap-1">
              <FolderSearch className="w-3.5 h-3.5 text-primary" /> File Scan Summary
            </span>
            <span className="text-primary font-mono font-bold">{actualScannedGb} GB Actual Scanned Files</span>
          </div>

          <div className="grid grid-cols-3 gap-2 text-center pt-1">
            <div className="bg-white p-2 rounded-lg border border-slate-200">
              <span className="text-[11px] text-slate-500 font-medium block">Examined</span>
              <strong className="text-sm font-bold text-slate-900">{filesExamined.toLocaleString()}</strong>
            </div>
            <div className="bg-white p-2 rounded-lg border border-slate-200">
              <span className="text-[11px] text-emerald-600 font-medium block">Accessible</span>
              <strong className="text-sm font-bold text-emerald-800">{filesAccessible.toLocaleString()}</strong>
            </div>
            <div className="bg-white p-2 rounded-lg border border-slate-200">
              <span className="text-[11px] text-amber-600 font-medium block">Skipped</span>
              <strong className="text-sm font-bold text-amber-800">{filesSkipped.toLocaleString()}</strong>
            </div>
          </div>

          {filesSkipped > 0 && (
            <p className="text-[11px] text-amber-800 font-medium flex items-center gap-1 pt-0.5">
              <AlertCircle className="w-3 h-3 text-amber-600 flex-shrink-0" />
              {filesSkipped.toLocaleString()} system-protected files skipped due to Windows access permissions.
            </p>
          )}
        </div>
      </div>

      {/* Category Metric Cards (Duplicate, Temp, Large Files) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {/* Duplicate Files */}
        <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 rounded-lg bg-amber-100 text-amber-700 font-bold">
              <Copy className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs font-bold text-slate-700 uppercase tracking-wider">Duplicate Files</p>
              <p className="text-base font-bold text-slate-900">
                {report.duplicateFilesSizeGb || 0} GB
                <span className="text-xs font-medium text-slate-600 ml-1.5">({report.duplicateFilesCount || 0} files)</span>
              </p>
            </div>
          </div>

          {duplicateGroupsList.length > 0 ? (
            <button
              onClick={() => setShowDuplicateGroups(!showDuplicateGroups)}
              className="text-xs font-bold text-amber-800 hover:text-amber-950 flex items-center gap-1 pt-1 cursor-pointer"
            >
              {showDuplicateGroups ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
              {showDuplicateGroups ? 'Hide Duplicate Groups' : `View ${duplicateGroupsList.length} SHA-256 Duplicate Groups`}
            </button>
          ) : (
            <p className="text-[11px] text-slate-500 font-medium pt-1">
              No duplicate files found in scanned locations.
            </p>
          )}
        </div>

        {/* Temp & Junk Files */}
        <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 rounded-lg bg-red-100 text-red-700 font-bold">
              <Trash2 className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs font-bold text-slate-700 uppercase tracking-wider">Temp & Junk Files</p>
              <p className="text-base font-bold text-slate-900">
                {report.tempJunkFilesSizeGb || 0} GB
                <span className="text-xs font-medium text-slate-600 ml-1.5">({report.tempJunkFilesCount || 0} items)</span>
              </p>
            </div>
          </div>
          <p className="text-[11px] text-slate-500 font-medium pt-1">
            Windows temp, app cache, and crash dump files. Read-only audit.
          </p>
        </div>

        {/* Large Files (>100MB) */}
        <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
          <div className="flex items-center space-x-3">
            <div className="p-2.5 rounded-lg bg-primary/10 text-primary font-bold">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <p className="text-xs font-bold text-slate-700 uppercase tracking-wider">Large Files (&gt;100MB)</p>
              <p className="text-base font-bold text-slate-900">
                {report.largeFilesSizeGb || 0} GB
                <span className="text-xs font-medium text-slate-600 ml-1.5">({report.largeFilesCount || 0} files)</span>
              </p>
            </div>
          </div>

          {largeFilesList.length > 0 ? (
            <button
              onClick={() => setShowLargeFiles(!showLargeFiles)}
              className="text-xs font-bold text-primary hover:text-primary-dark flex items-center gap-1 pt-1 cursor-pointer"
            >
              {showLargeFiles ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
              {showLargeFiles ? 'Hide Large File Details' : `View ${largeFilesList.length} Large Files`}
            </button>
          ) : (
            <p className="text-[11px] text-slate-500 font-medium pt-1">
              No files larger than 100 MB were found in scanned locations.
            </p>
          )}
        </div>
      </div>

      {/* Expandable Large Files Detailed Table */}
      {showLargeFiles && largeFilesList.length > 0 && (
        <div className="p-4 rounded-xl bg-slate-900 text-slate-100 space-y-2 animate-fade-in">
          <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center gap-1.5 border-b border-slate-700 pb-2">
            <Sparkles className="w-4 h-4 text-primary" /> Large Files Exceeding 100 MB
          </h4>
          <div className="max-h-60 overflow-y-auto space-y-1.5 pr-1">
            {largeFilesList.map((lf, idx) => (
              <div key={idx} className="p-2 rounded bg-slate-800/80 text-xs flex justify-between items-center font-mono">
                <div className="truncate mr-3">
                  <span className="text-cyan-400 font-bold block truncate">{lf.fileName}</span>
                  <span className="text-slate-400 text-[10px] block truncate">{lf.filePath}</span>
                </div>
                <div className="text-right flex-shrink-0">
                  <span className="text-amber-400 font-bold block">{lf.sizeMb} MB</span>
                  <span className="text-slate-400 text-[10px]">{lf.extension ? `.${lf.extension}` : 'File'}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Expandable Duplicate Groups Detailed List */}
      {showDuplicateGroups && duplicateGroupsList.length > 0 && (
        <div className="p-4 rounded-xl bg-slate-900 text-slate-100 space-y-2 animate-fade-in">
          <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center gap-1.5 border-b border-slate-700 pb-2">
            <Copy className="w-4 h-4 text-amber-400" /> SHA-256 Verified Duplicate Groups
          </h4>
          <div className="max-h-60 overflow-y-auto space-y-2.5 pr-1">
            {duplicateGroupsList.map((grp, idx) => (
              <div key={idx} className="p-2.5 rounded bg-slate-800/80 text-xs font-mono space-y-1">
                <div className="flex justify-between items-center text-slate-300 border-b border-slate-700/60 pb-1">
                  <span>SHA-256: <strong className="text-cyan-400">{grp.sha256 ? grp.sha256.substring(0, 16) + '...' : 'Hash'}</strong></span>
                  <span>Recoverable: <strong className="text-emerald-400">{grp.recoverableMb} MB</strong> ({grp.fileCount} duplicates)</span>
                </div>
                <div className="space-y-0.5 pt-0.5">
                  {Array.isArray(grp.filePaths) && grp.filePaths.map((p, pIdx) => (
                    <span key={pIdx} className="text-slate-400 text-[11px] block truncate pl-2">
                      • {p}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Scanned Locations Audit Scope */}
      {scannedLocations.length > 0 && (
        <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 space-y-1">
          <span className="text-xs font-bold text-slate-700 uppercase tracking-wider block">Scanned Directory Scope</span>
          <div className="flex flex-wrap gap-1.5 pt-0.5">
            {scannedLocations.map((loc, idx) => (
              <span key={idx} className="text-[11px] font-mono font-medium px-2 py-0.5 rounded bg-white border border-slate-200 text-slate-800">
                {loc}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Part 12: Actionable Storage Recommendations Generated strictly from Real Scan Results */}
      {report.optimizationSuggestions && report.optimizationSuggestions.length > 0 && (
        <div className="p-4 rounded-xl bg-primary-container/10 border border-primary/20">
          <h4 className="text-body-md font-bold text-primary flex items-center gap-1.5 mb-2">
            <Sparkles className="w-4 h-4" /> Real Measurement Storage Recommendations
          </h4>
          <ul className="space-y-1.5 text-body-md text-slate-800 font-medium">
            {report.optimizationSuggestions.map((s, idx) => (
              <li key={idx} className="flex items-start gap-2 text-xs leading-relaxed">
                <CheckCircle className="w-3.5 h-3.5 text-primary flex-shrink-0 mt-0.5" />
                <span>{s}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};

export default FileAnalyzerCard;
