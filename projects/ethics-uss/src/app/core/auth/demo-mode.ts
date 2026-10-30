/** El selector demo no es la identidad. Producción lo apaga aunque la bandera local esté activa. */
export function demoSelectorVisible(demoEnabled: boolean, production: boolean): boolean {
  return demoEnabled === true && production !== true;
}
