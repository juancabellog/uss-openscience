import { I18nHostDirective } from './i18n-host.directive';
import { LocaleDatePipe } from '../pipes/locale-date.pipe';
import { LocaleNumberPipe } from '../pipes/locale-number.pipe';
import { LocalePercentPipe } from '../pipes/locale-percent.pipe';
import { TranslatePipe } from '../pipes/translate.pipe';

export const I18N_IMPORTS = [TranslatePipe, LocaleDatePipe, LocaleNumberPipe, LocalePercentPipe] as const;
export const I18N_HOST = [{ directive: I18nHostDirective }] as const;
