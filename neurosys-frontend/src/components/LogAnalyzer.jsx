import React, { useState, useEffect } from 'react';
import { metricsService } from '../services/metricsService';
import { FileText, AlertTriangle, CheckCircle, Info, ChevronDown, ChevronUp, Filter } from 'lucide-react';

const LogAnalyzer = ({ computerId, logs: propLogs }) => {
  const [logs, setLogs] = useState(propLogs || []);
  const [loading, setLoading] = useState(!propLogs);
  const [severityFilter, setSeverityFilter] = useState('PROBLEMS_ONLY'); // 'PROBLEMS_ONLY' | 'ALL'
  const [expandedDetails, setExpandedDetails] = useState({});

  useEffect(() => {
    if (!propLogs && computerId) {
      fetchLogs();
    }
  }, [computerId, propLogs]);

  const fetchLogs = async () => {
    try {
      const data = await metricsService.getLogs(computerId);
      const list = Array.isArray(data) ? data : (data?.data || data?.content || []);
      if (Array.isArray(list)) {
        setLogs(list);
      }
    } catch (e) {
      console.error('Error fetching logs for computer', e);
    } finally {
      setLoading(false);
    }
  };

  const toggleTechnicalDetails = (id) => {
    setExpandedDetails((prev) => ({
      ...prev,
      [id]: !prev[id]
    }));
  };

  const filteredLogs = logs.filter((log) => {
    if (severityFilter === 'PROBLEMS_ONLY') {
      const sev = (log.severity || log.logLevel || '').toUpperCase();
      return sev.includes('CRIT') || sev.includes('WARN') || sev.includes('ERR');
    }
    return true;
  });

  return (
    <div className="card-elevated rounded-xl p-6 border border-slate-200 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-200">
        <h3 className="text-headline-md font-headline-md text-slate-900 font-bold flex items-center gap-2">
          <FileText className="w-5 h-5 text-primary" /> Windows Diagnostic Log Analysis
        </h3>
        
        <div className="flex items-center gap-2">
          <Filter className="w-4 h-4 text-slate-500" />
          <select
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value)}
            className="text-xs font-bold px-2.5 py-1.5 rounded-lg border border-slate-300 bg-white text-slate-800 focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer"
          >
            <option value="PROBLEMS_ONLY">Actionable Problems (Warning & Critical)</option>
            <option value="ALL">All Events (Include Informational)</option>
          </select>
        </div>
      </div>

      <div className="space-y-3">
        {loading ? (
          <div className="p-8 text-center text-slate-700 text-body-md font-semibold">
            Analyzing Windows diagnostic logs...
          </div>
        ) : (!filteredLogs || filteredLogs.length === 0) ? (
          <div className="p-8 text-center text-slate-700 text-body-md font-semibold border border-dashed border-slate-200 rounded-xl bg-slate-50 space-y-1">
            <CheckCircle className="w-8 h-8 text-emerald-600 mx-auto mb-2" />
            <p className="text-slate-900 font-bold text-base">No Actionable Diagnostic Problems Detected</p>
            <p className="text-slate-600 text-sm font-normal">
              {severityFilter === 'PROBLEMS_ONLY'
                ? 'No warning or critical event issues found. System operating normally.'
                : 'No event logs collected yet for this workstation.'}
            </p>
          </div>
        ) : (
          filteredLogs.map((log, idx) => {
            const logId = log.id || idx;
            const isExpanded = !!expandedDetails[logId];
            const severity = (log.severity || log.logLevel || 'INFO').toUpperCase();
            const isCritical = severity.includes('CRIT') || severity.includes('ERR');
            const isWarning = severity.includes('WARN');

            const title = log.title || log.eventCategory || `Windows Event ${log.eventId || ''}`;
            const category = log.eventCategory || (isCritical ? 'Critical Error' : isWarning ? 'System Warning' : 'System Notice');

            return (
              <div
                key={logId}
                className={`p-4 rounded-xl border transition-colors space-y-3 ${
                  isCritical
                    ? 'bg-red-50/40 border-red-200'
                    : isWarning
                    ? 'bg-amber-50/40 border-amber-200'
                    : 'bg-slate-50 border-slate-200'
                }`}
              >
                {/* Primary Header & Severity Badge */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="flex items-center space-x-2.5">
                    {isCritical ? (
                      <AlertTriangle className="w-5 h-5 text-red-600 flex-shrink-0 font-bold" />
                    ) : isWarning ? (
                      <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 font-bold" />
                    ) : (
                      <Info className="w-5 h-5 text-primary flex-shrink-0 font-bold" />
                    )}
                    <div>
                      <span className="text-xs font-bold px-2 py-0.5 rounded-full mr-2 bg-white/80 border border-slate-300 text-slate-800">
                        {category}
                      </span>
                      <h4 className="text-base font-bold text-slate-900 inline">{title}</h4>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 self-end sm:self-auto">
                    <span
                      className={`text-xs font-extrabold px-2.5 py-0.5 rounded-full ${
                        isCritical
                          ? 'bg-red-100 text-red-800 border border-red-300'
                          : isWarning
                          ? 'bg-amber-100 text-amber-800 border border-amber-300'
                          : 'bg-blue-100 text-blue-800 border border-blue-300'
                      }`}
                    >
                      {severity}
                    </span>
                    <span className="text-xs font-medium text-slate-500">
                      {log.timestamp ? new Date(log.timestamp).toLocaleString() : 'Recently'}
                    </span>
                  </div>
                </div>

                {/* Structured Explanation Cards */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm pt-1">
                  {/* What Happened */}
                  <div className="p-3 rounded-lg bg-white/80 border border-slate-200/80 space-y-1">
                    <span className="text-xs font-bold text-slate-500 uppercase tracking-wider block">What Happened</span>
                    <p className="text-slate-800 font-medium text-xs leading-relaxed">
                      {log.whatHappened || log.simplifiedEnglish || log.message || log.rawMessage}
                    </p>
                  </div>

                  {/* Why It Matters */}
                  <div className="p-3 rounded-lg bg-white/80 border border-slate-200/80 space-y-1">
                    <span className="text-xs font-bold text-slate-500 uppercase tracking-wider block">Why It Matters</span>
                    <p className="text-slate-800 font-medium text-xs leading-relaxed">
                      {log.whyItMatters || 'System component logged an exception that may impact stability if recurring.'}
                    </p>
                  </div>

                  {/* Recommended Action */}
                  <div className="p-3 rounded-lg bg-emerald-50/80 border border-emerald-200 space-y-1">
                    <span className="text-xs font-bold text-emerald-800 uppercase tracking-wider block flex items-center gap-1">
                      <CheckCircle className="w-3.5 h-3.5 text-emerald-600" /> Recommended Action
                    </span>
                    <p className="text-emerald-950 font-medium text-xs leading-relaxed">
                      {log.recommendedAction || log.suggestedSolution || 'Review device manager and maintain routine system updates.'}
                    </p>
                  </div>
                </div>

                {/* Technical Details Collapsible Toggle */}
                <div className="pt-1">
                  <button
                    onClick={() => toggleTechnicalDetails(logId)}
                    className="text-xs font-bold text-slate-600 hover:text-slate-900 flex items-center gap-1 transition-colors cursor-pointer"
                  >
                    {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                    {isExpanded ? 'Hide Technical Details' : 'Show Technical Details (Event ID & Provider)'}
                  </button>

                  {isExpanded && (
                    <div className="mt-2.5 p-3 rounded-lg bg-slate-900 text-slate-100 text-xs font-mono space-y-1.5 animate-fade-in">
                      <div className="flex flex-wrap justify-between gap-2 border-b border-slate-700 pb-1.5 text-slate-400">
                        <span>Event ID: <strong className="text-amber-400">{log.eventId || 'N/A'}</strong></span>
                        <span>Provider: <strong className="text-cyan-400">{log.providerName || log.sourceComponent || 'Windows Kernel'}</strong></span>
                        <span>Log Level: <strong className="text-slate-200">{log.logLevel}</strong></span>
                      </div>
                      <div className="pt-1">
                        <span className="text-slate-400 block mb-1">Raw Event Message Payload:</span>
                        <pre className="text-slate-300 font-mono text-[11px] whitespace-pre-wrap break-words max-h-40 overflow-y-auto">
                          {log.rawMessage || log.message || 'No raw message string captured.'}
                        </pre>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default LogAnalyzer;
