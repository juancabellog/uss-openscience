import { AuthPermission, CurrentUser } from '../../models/openscience.models';

export interface AuthUserDto extends CurrentUser {}

export interface GenericMessageDto {
  code: string;
  message: string;
  emailSent?: boolean;
}

export interface AuthPublicConfigDto {
  registrationEnabled: boolean;
  registrationUssOnly: boolean;
}

export const SAFE_AUTH_PUBLIC_CONFIG: AuthPublicConfigDto = {
  registrationEnabled: true,
  registrationUssOnly: true
};

export interface RegisterRequestDto {
  firstName: string;
  lastName: string;
  email: string;
}

export interface RegisterResultDto {
  code: string;
  message: string;
  outcome: 'CREATED' | 'EXISTS_ACTIVE' | 'EXISTS_PENDING' | 'EXISTS_UNAVAILABLE' | string;
  emailSent?: boolean;
  canResend?: boolean;
  maskedEmail?: string | null;
}

export interface ActivationPreviewDto {
  valid: boolean;
  status?: 'VALID' | 'INVALID' | 'EXPIRED' | 'USED' | 'ALREADY_ACTIVE' | string;
  maskedEmail?: string | null;
  purpose?: string;
  profileRequired?: boolean;
  givenName?: string | null;
  familyName?: string | null;
  preferredLanguage?: string | null;
}

export interface AuthPermissionDto extends AuthPermission {}
