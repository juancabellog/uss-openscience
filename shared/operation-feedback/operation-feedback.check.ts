import assert from 'node:assert/strict';
import {
  OPERATION_DETAIL_MS,
  OPERATION_INDICATOR_MS,
  OPERATION_LONG_MS,
  OperationRun,
  operationButton
} from './operation-feedback.ts';

const clock = { t: 1_000 };
const run = new OperationRun(() => clock.t);
const copy = {
  loadingMessage: 'Creando usuario...',
  detailMessage: 'Estamos preparando el acceso y enviando la invitación.',
  longRunningMessage: 'Esto está tardando más de lo habitual. Seguimos procesando la solicitud.',
  blocking: true
};

assert.equal(run.start(copy), true, 'the first start is accepted');
assert.equal(run.start(copy), false, 'a second start does not open another operation');
let view = run.view();
assert.equal(view.running, true);
assert.equal(view.showPanel, false, 'a fast operation does not flash the blocking panel');
assert.equal(view.detailMessage, '');
assert.equal(view.longRunningMessage, '');
const button = operationButton(view, 'Crear usuario');
assert.equal(button.disabled, true);
assert.equal(button.label, 'Creando usuario...');
assert.equal(button.spinner, true);

clock.t += OPERATION_INDICATOR_MS;
run.observe();
view = run.view();
assert.equal(view.showIndicator, true);
assert.equal(view.showPanel, true);
assert.equal(view.detailMessage, '');

clock.t += OPERATION_DETAIL_MS;
run.observe();
view = run.view();
assert.equal(view.showDetail, true);
assert.match(view.detailMessage, /preparando el acceso/);
assert.equal(view.longRunningMessage, '');

clock.t += OPERATION_LONG_MS;
run.observe();
view = run.view();
assert.match(view.longRunningMessage, /tardando más de lo habitual/);

run.succeed('Invitación enviada');
view = run.view();
assert.equal(view.running, false);
assert.equal(view.phase, 'success');
assert.equal(view.showPanel, false);
assert.equal(view.longRunningMessage, '');
assert.equal(view.successMessage, 'Invitación enviada');
assert.deepEqual(operationButton(view, 'Crear usuario'), { disabled: false, label: 'Crear usuario', spinner: false });

assert.equal(run.start(copy), true, 'a finished operation can run again');
run.fail('No fue posible enviar la invitación.');
view = run.view();
assert.equal(view.phase, 'error');
assert.equal(view.running, false);
assert.equal(view.showPanel, false);
assert.equal(view.longRunningMessage, '');
assert.equal(view.errorMessage, 'No fue posible enviar la invitación.');
assert.equal(operationButton(view, 'Crear usuario').disabled, false);

const local = new OperationRun(() => clock.t);
assert.equal(local.start({ loadingMessage: 'Guardando...', blocking: false }), true);
clock.t += OPERATION_DETAIL_MS + OPERATION_INDICATOR_MS;
local.observe();
assert.equal(local.view().showPanel, false, 'a localized operation does not cover the page');

console.log('operation-feedback.check OK');
