import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ActivationPreviewDto,
  AuthPublicConfigDto,
  AuthUserDto,
  GenericMessageDto,
  RegisterRequestDto,
  RegisterResultDto
} from './auth-api.types';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/auth`;

  login(email: string, password: string): Observable<AuthUserDto> {
    return this.http.post<AuthUserDto>(`${this.base}/login`, { email, password }, { withCredentials: true });
  }

  logout(): Observable<GenericMessageDto> {
    return this.http.post<GenericMessageDto>(`${this.base}/logout`, {}, { withCredentials: true });
  }

  me(): Observable<AuthUserDto> {
    return this.http.get<AuthUserDto>(`${this.base}/me`, { withCredentials: true });
  }

  publicConfig(): Observable<AuthPublicConfigDto> {
    return this.http.get<AuthPublicConfigDto>(`${this.base}/config`, { withCredentials: true });
  }

  register(body: RegisterRequestDto): Observable<RegisterResultDto> {
    return this.http.post<RegisterResultDto>(`${this.base}/register`, body, { withCredentials: true });
  }

  forgotPassword(email: string): Observable<GenericMessageDto> {
    return this.http.post<GenericMessageDto>(`${this.base}/forgot-password`, { email }, { withCredentials: true });
  }

  previewActivate(token: string): Observable<ActivationPreviewDto> {
    return this.http.get<ActivationPreviewDto>(`${this.base}/activate`, { params: { token }, withCredentials: true });
  }

  activate(token: string, password: string): Observable<GenericMessageDto> {
    return this.http.post<GenericMessageDto>(`${this.base}/activate`, { token, password }, { withCredentials: true });
  }

  previewReset(token: string): Observable<ActivationPreviewDto> {
    return this.http.get<ActivationPreviewDto>(`${this.base}/reset-password`, { params: { token }, withCredentials: true });
  }

  resetPassword(token: string, password: string): Observable<GenericMessageDto> {
    return this.http.post<GenericMessageDto>(`${this.base}/reset-password`, { token, password }, { withCredentials: true });
  }

  resendActivation(email: string): Observable<GenericMessageDto> {
    return this.http.post<GenericMessageDto>(`${this.base}/resend-activation`, { email }, { withCredentials: true });
  }
}
