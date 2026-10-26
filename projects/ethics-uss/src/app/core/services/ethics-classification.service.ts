import { Injectable } from '@angular/core';
import { classifyStudyObjects } from '../../mocks/classification-rules.mock';
import { ClassificationCode } from '../models/ethics.models';

@Injectable({ providedIn: 'root' })
export class EthicsClassificationService {
  suggest(studyObjectIds: string[]): ClassificationCode[] {
    return classifyStudyObjects(studyObjectIds);
  }
}
