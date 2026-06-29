import type { FormInstance } from 'antd';
import type { AiModel } from '../../../types';
import type { ModelFormValues } from './ModelFormModal';

/** 表单值（数组）转成后端需要的字符串（逗号分隔）+ 拼装 sortOrder / id */
export const valuesToPayload = (
  values: ModelFormValues,
  extra: { sortOrder: number; id?: number },
): Partial<AiModel> => ({
  id: extra.id,
  name: values.name,
  provider: values.provider,
  identifier: values.identifier,
  baseUrl: values.baseUrl,
  apiKey: values.apiKey,
  capabilities: values.capabilities.join(','),
  description: values.description,
  isDefault: values.isDefault ? 'true' : 'false',
  sortOrder: extra.sortOrder,
});

/** 新建时填默认值 */
export const getDefaultFormValues = (): Partial<ModelFormValues> => ({
  isDefault: false,
  capabilities: ['text'],
});

/** 编辑时把后端数据填回表单 */
export const fillFormForEdit = (
  form: FormInstance<ModelFormValues>,
  model: AiModel,
) => {
  form.setFieldsValue({
    name: model.name,
    provider: model.provider,
    identifier: model.identifier,
    baseUrl: model.baseUrl,
    apiKey: undefined,
    capabilities: model.capabilities ? model.capabilities.split(',') : [],
    description: model.description,
    isDefault: model.isDefault === 'true',
  });
};
