import { CatalogPipe } from '../pipes/catalog.pipe';
import { LocaleDatePipe } from '../pipes/locale-date.pipe';
import { TranslatePipe } from '../pipes/translate.pipe';

export const I18N_IMPORTS = [TranslatePipe, LocaleDatePipe, CatalogPipe] as const;
