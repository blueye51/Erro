import type { ChatReply } from './chat'

function Notes({ title, items }: { title: string; items: string[] }) {
  if (!items.length) return null
  return <details className="answer-detail"><summary>{title} ({items.length})</summary><ul>{items.map((item, i) => <li key={i}>{item}</li>)}</ul></details>
}
export function AnswerDetails({ answer }: { answer: ChatReply }) {
  return <div className="answer-details">
    {answer.validations.some(c => c.status === 'FAIL') && <p className="validation-failure" role="note">A stated electrical requirement failed validation. See checks below.</p>}
    {answer.sources.length > 0 && <details className="answer-detail"><summary>Sources ({answer.sources.length})</summary>
      <ol>{answer.sources.map(source => <li key={source.id}>
        {/^https?:\/\//.test(source.url) ? <a href={source.url} target="_blank" rel="noopener noreferrer">{source.title}</a> : <strong>{source.title}</strong>}
        <p>{source.publisher} · {source.section} · {source.jurisdiction} · {source.sourceType.replaceAll('_', ' ').toLowerCase()}</p>
        <p>{[source.standardNumber, source.documentVersion, source.edition, source.amendment].filter(Boolean).join(' · ')}</p>
        {source.copyrightStatus === 'METADATA_ONLY' && <p>Scope/metadata only; full requirements were not retrieved.</p>}
        {source.licenseNotes && <p>{source.licenseNotes}</p>}
      </li>)}</ol></details>}
    {answer.calculations.length > 0 && <details className="answer-detail"><summary>Calculations ({answer.calculations.length})</summary>
      {answer.calculations.map((c, i) => <div key={i}><p><strong>{c.name}: {c.value} {c.unit}</strong></p><p>{c.formula}</p>
        <dl>{Object.entries(c.inputs).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl></div>)}
    </details>}
    <Notes title="Assumptions" items={answer.assumptions} />
    <Notes title="Missing information" items={answer.missingInformation} />
    <Notes title="Warnings" items={answer.warnings} />
    {answer.validations.length > 0 && <details className="answer-detail"><summary>Electrical checks ({answer.validations.length})</summary>
      <ul>{answer.validations.map((c, i) => <li key={i}><strong>{c.status}</strong> — {c.explanation}</li>)}</ul>
    </details>}
    {answer.products.length > 0 && <details className="answer-detail"><summary>Catalog candidates ({answer.products.length})</summary>
      <p>Candidates require further checks before selection.</p>
      {answer.products.map(p => <div key={p.id}><strong>{String(p.specifications.manufacturer)} — {String(p.specifications.part_number)}</strong>
        <ul>{p.checks.map((c, i) => <li key={i}>{c.status}: {c.explanation}</li>)}</ul></div>)}
    </details>}
  </div>
}
